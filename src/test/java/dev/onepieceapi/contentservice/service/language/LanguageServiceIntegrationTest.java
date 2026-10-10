package dev.onepieceapi.contentservice.service.language;

import dev.onepieceapi.contentservice.service.audit.AuditLogService;

import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.language.Language;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.exception.InvalidLanguageCodeException;
import dev.onepieceapi.contentservice.service.exception.InvalidLanguageNameException;
import dev.onepieceapi.contentservice.service.exception.LanguageAlreadyExistsException;
import dev.onepieceapi.contentservice.service.exception.LanguageInUseException;
import dev.onepieceapi.contentservice.service.exception.LanguageNotFoundException;
import dev.onepieceapi.contentservice.service.validation.LanguageValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Runs the language catalog CRUD against a real PostgreSQL (Testcontainers), so the
 * Flyway-seeded catalog and the audit trail are the real ones.
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
	private AuditLogRepository auditLogRepository;

	@Autowired
	private DevilFruitTypeVersionRepository versionRepository;

	@Autowired
	private TestEntityManager entityManager;

	private LanguageService service;

	private final User admin = new User(UUID.randomUUID(), "luffy", "admin@onepiece.local");

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		this.service = new LanguageService(this.languageRepository, List.of(this.versionRepository), auditLogService,
				new LanguageValidator());
	}

	@Test
	void deleteIsRefusedWhileAVersionCarriesATranslationInTheLanguage() {
		var author = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");
		var content = this.entityManager
			.persist(new ContentEntity(UUID.randomUUID(), EntityType.DEVIL_FRUIT_TYPE, Instant.EPOCH));
		var workflow = ContentVersionEntity.builder()
			.contentId(content.getId())
			.versionNumber(1)
			.author(UserMapper.toEmbeddable(author))
			.status(VersionStatus.DRAFT)
			.createdAt(Instant.EPOCH)
			.updatedAt(Instant.EPOCH)
			.build();
		var draft = new DevilFruitTypeVersionEntity(workflow);
		draft.getTranslations().put("it", new TranslationEmbeddable("Paramisia", null, null, null));
		this.versionRepository.saveAndFlush(draft);

		assertThatThrownBy(() -> this.service.delete("it", this.admin)).isInstanceOf(LanguageInUseException.class);
		assertThat(this.languageRepository.existsById("it")).isTrue();
		// English is in no version: still free to go.
		this.service.delete("en", this.admin);
		assertThat(this.languageRepository.existsById("en")).isFalse();
	}

	@Test
	void listsTheSeededCatalogInOrder() {
		assertThat(this.service.list()).containsExactly(new Language("en", "English"), new Language("it", "Italiano"));
	}

	@Test
	void createAddsANewLanguageAndNormalizesItsCode() {
		var created = this.service.create(" FR ", "Français", this.admin);

		assertThat(created).isEqualTo(new Language("fr", "Français"));
		assertThat(this.languageRepository.existsById("fr")).isTrue();
		assertThat(this.auditLogRepository.findAll()).anySatisfy(entry -> {
			assertThat(entry.getAction()).isEqualTo("LANGUAGE_CREATED");
			assertThat(entry.getTargetLabel()).isEqualTo("fr");
			assertThat(entry.getTargetContentId()).isNull();
		});
	}

	@Test
	void createRejectsADuplicateCode() {
		assertThatThrownBy(() -> this.service.create("it", "Italiano", this.admin))
			.isInstanceOf(LanguageAlreadyExistsException.class);
	}

	@Test
	void createRejectsAnInvalidCode() {
		assertThatThrownBy(() -> this.service.create("123", "Numbers", this.admin))
			.isInstanceOf(InvalidLanguageCodeException.class);
		assertThatThrownBy(() -> this.service.create("fra", "Français", this.admin))
			.isInstanceOf(InvalidLanguageCodeException.class);
		assertThatThrownBy(() -> this.service.create("", "Empty", this.admin))
			.isInstanceOf(InvalidLanguageCodeException.class);
	}

	@Test
	void createRejectsABlankName() {
		assertThatThrownBy(() -> this.service.create("fr", "  ", this.admin))
			.isInstanceOf(InvalidLanguageNameException.class);
		assertThatThrownBy(() -> this.service.create("fr", null, this.admin))
			.isInstanceOf(InvalidLanguageNameException.class);
	}

	@Test
	void deleteRemovesALanguage() {
		this.service.create("fr", "Français", this.admin);

		this.service.delete("fr", this.admin);

		assertThat(this.languageRepository.existsById("fr")).isFalse();
	}

	@Test
	void deleteFailsForAnUnknownCode() {
		assertThatThrownBy(() -> this.service.delete("xx", this.admin)).isInstanceOf(LanguageNotFoundException.class);
	}

}
