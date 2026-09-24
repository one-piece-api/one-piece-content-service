package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionTranslationEntity;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionTranslationRepository;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeItemEntity;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeItemRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEntity;
import dev.onepieceapi.contentservice.persistence.repository.TranslationRepository;
import dev.onepieceapi.contentservice.persistence.entity.WorkingRevisionEntity;
import dev.onepieceapi.contentservice.persistence.repository.WorkingRevisionRepository;
import dev.onepieceapi.contentservice.persistence.entity.WorkingRevisionStatus;
import dev.onepieceapi.contentservice.service.exception.InvalidLanguageCodeException;
import dev.onepieceapi.contentservice.service.exception.InvalidLanguageNameException;
import dev.onepieceapi.contentservice.service.exception.LanguageAlreadyExistsException;
import dev.onepieceapi.contentservice.service.exception.LanguageInUseException;
import dev.onepieceapi.contentservice.service.exception.LanguageNotFoundException;
import dev.onepieceapi.contentservice.web.dto.LanguageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Runs Step 10's language catalog CRUD against a real PostgreSQL (Testcontainers) - the
 * "already referenced, can't delete" rule depends on the real {@code REFERENCES} foreign
 * key on both translation tables (V3/V5), which an in-memory mock repository can't
 * exercise.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class LanguageServiceIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	@Autowired
	private LanguageRepository languageRepository;

	@Autowired
	private TranslationRepository translationRepository;

	@Autowired
	private ContentVersionTranslationRepository contentVersionTranslationRepository;

	@Autowired
	private DevilFruitTypeItemRepository itemRepository;

	@Autowired
	private WorkingRevisionRepository workingRevisionRepository;

	@Autowired
	private ContentVersionRepository contentVersionRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	private LanguageService service;

	private final UUID admin = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		this.service = new LanguageService(this.languageRepository, this.translationRepository,
				this.contentVersionTranslationRepository, auditLogService);
	}

	@Test
	void listsTheSeededCatalogInOrder() {
		assertThat(this.service.list()).containsExactly(new LanguageResponse("en", "English"),
				new LanguageResponse("it", "Italiano"));
	}

	@Test
	void createAddsANewLanguageAndNormalizesItsCode() {
		var created = this.service.create(" FR ", "Français", this.admin, "admin@onepiece.local");

		assertThat(created).isEqualTo(new LanguageResponse("fr", "Français"));
		assertThat(this.languageRepository.existsById("fr")).isTrue();
		assertThat(this.auditLogRepository.findAll()).anySatisfy(entry -> {
			assertThat(entry.getAction()).isEqualTo("LANGUAGE_CREATED");
			assertThat(entry.getTargetLabel()).isEqualTo("fr");
			assertThat(entry.getTargetItemId()).isNull();
		});
	}

	@Test
	void createRejectsADuplicateCode() {
		assertThatThrownBy(() -> this.service.create("it", "Italiano", this.admin, "admin@onepiece.local"))
			.isInstanceOf(LanguageAlreadyExistsException.class);
	}

	@Test
	void createRejectsAnInvalidCode() {
		assertThatThrownBy(() -> this.service.create("123", "Numbers", this.admin, "admin@onepiece.local"))
			.isInstanceOf(InvalidLanguageCodeException.class);
		assertThatThrownBy(() -> this.service.create("fra", "Français", this.admin, "admin@onepiece.local"))
			.isInstanceOf(InvalidLanguageCodeException.class);
		assertThatThrownBy(() -> this.service.create("", "Empty", this.admin, "admin@onepiece.local"))
			.isInstanceOf(InvalidLanguageCodeException.class);
	}

	@Test
	void createRejectsABlankName() {
		assertThatThrownBy(() -> this.service.create("fr", "  ", this.admin, "admin@onepiece.local"))
			.isInstanceOf(InvalidLanguageNameException.class);
		assertThatThrownBy(() -> this.service.create("fr", null, this.admin, "admin@onepiece.local"))
			.isInstanceOf(InvalidLanguageNameException.class);
	}

	@Test
	void deleteRemovesAnUnreferencedLanguage() {
		this.service.create("fr", "Français", this.admin, "admin@onepiece.local");

		this.service.delete("fr", this.admin, "admin@onepiece.local");

		assertThat(this.languageRepository.existsById("fr")).isFalse();
	}

	@Test
	void deleteFailsForAnUnknownCode() {
		assertThatThrownBy(() -> this.service.delete("xx", this.admin, "admin@onepiece.local"))
			.isInstanceOf(LanguageNotFoundException.class);
	}

	@Test
	void deleteFailsWhileAWorkingRevisionStillReferencesTheLanguage() {
		var now = Instant.parse("2026-09-22T10:00:00Z");
		var item = this.itemRepository.save(new DevilFruitTypeItemEntity(UUID.randomUUID(), now));
		var revision = this.workingRevisionRepository.save(new WorkingRevisionEntity(UUID.randomUUID(), item.getId(),
				UUID.randomUUID(), "editor@onepiece.local", WorkingRevisionStatus.DRAFT, now));
		this.translationRepository.save(new TranslationEntity(revision.getId(), "it", "Paramecia", "Descrizione"));

		assertThatThrownBy(() -> this.service.delete("it", this.admin, "admin@onepiece.local"))
			.isInstanceOf(LanguageInUseException.class);
		assertThat(this.languageRepository.existsById("it")).isTrue();
	}

	@Test
	void deleteFailsWhileAPublishedVersionStillReferencesTheLanguage() {
		var now = Instant.parse("2026-09-22T10:00:00Z");
		var item = this.itemRepository.save(new DevilFruitTypeItemEntity(UUID.randomUUID(), now));
		var version = this.contentVersionRepository.save(new ContentVersionEntity(UUID.randomUUID(), item.getId(), 1,
				"Paramishia", UUID.randomUUID(), "publisher@onepiece.local", now));
		this.contentVersionTranslationRepository
			.save(new ContentVersionTranslationEntity(version.getId(), "it", "Paramecia", "Descrizione"));

		assertThatThrownBy(() -> this.service.delete("it", this.admin, "admin@onepiece.local"))
			.isInstanceOf(LanguageInUseException.class);
		assertThat(this.languageRepository.existsById("it")).isTrue();
	}

}
