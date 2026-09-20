package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.persistence.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.DevilFruitTypeItemRepository;
import dev.onepieceapi.contentservice.persistence.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.TranslationRepository;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionRepository;
import dev.onepieceapi.contentservice.web.dto.TranslationRequest;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Runs UF-CNT-01/02 against a real PostgreSQL (Testcontainers), exercising the Flyway
 * migrations and JPA mapping together - see docs/implementation-plan-content.md Step 1's
 * QA acceptance: draft isolation is verified with two real accounts, not assumed.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
// @DataJpaTest's own auto-configuration list doesn't include Flyway (it normally assumes
// Hibernate generates the schema) - explicitly pulled in here since this schema is
// Flyway-managed and spring.jpa.hibernate.ddl-auto=validate needs it to already exist.
// Same
// pattern as one-piece-user-service's JpaAuditLogAdapterTest.
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitTypeServiceIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	@Autowired
	private DevilFruitTypeItemRepository itemRepository;

	@Autowired
	private WorkingRevisionRepository workingRevisionRepository;

	@Autowired
	private TranslationRepository translationRepository;

	@Autowired
	private LanguageRepository languageRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	private DevilFruitTypeService service;

	private final UUID editorA = UUID.randomUUID();

	private final UUID editorB = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		this.service = new DevilFruitTypeService(this.itemRepository, this.workingRevisionRepository,
				this.translationRepository, this.languageRepository, auditLogService, clock);
	}

	@Test
	void aNewDraftIsEmptyAndOwnedByItsCreator() {
		var revision = this.service.createDraft(this.editorA, "editor-a@onepiece.local");

		assertThat(revision.getAuthorId()).isEqualTo(this.editorA);
		assertThat(revision.getStatus().name()).isEqualTo("DRAFT");
		assertThat(revision.getRomaji()).isNull();
	}

	@Test
	void editingSavesRomajiAndTranslationsForEveryLanguageProvided() {
		var revision = this.service.createDraft(this.editorA, "editor-a@onepiece.local");

		var updated = this.service.updateDraft(revision.getId(), this.editorA, "editor-a@onepiece.local", "Paramishia",
				Map.of("it", new TranslationRequest("Paramecia", "Descrizione IT"), "en",
						new TranslationRequest("", "")));

		assertThat(updated.getRomaji()).isEqualTo("Paramishia");
		var translations = this.service.translationsOf(updated.getId());
		assertThat(translations).hasSize(2);
	}

	@Test
	void editingRejectsAnUnknownLanguageCode() {
		var revision = this.service.createDraft(this.editorA, "editor-a@onepiece.local");

		assertThatThrownBy(() -> this.service.updateDraft(revision.getId(), this.editorA, "editor-a@onepiece.local",
				"Paramishia", Map.of("fr", new TranslationRequest("Paramécie", "..."))))
			.isInstanceOf(UnknownLanguageException.class);
	}

	@Test
	void aDraftIsInvisibleToEveryAuthorButItsOwn() {
		var revision = this.service.createDraft(this.editorA, "editor-a@onepiece.local");

		assertThat(this.service.listOwnDrafts(this.editorA)).extracting(r -> r.getId())
			.containsExactly(revision.getId());
		assertThat(this.service.listOwnDrafts(this.editorB)).isEmpty();

		assertThatThrownBy(() -> this.service.getOwnDraft(revision.getId(), this.editorB))
			.isInstanceOf(WorkingRevisionNotFoundException.class);
		assertThatThrownBy(() -> this.service.updateDraft(revision.getId(), this.editorB, "editor-b@onepiece.local",
				"Hijack", Map.of()))
			.isInstanceOf(WorkingRevisionNotFoundException.class);
	}

	@Test
	void gettingAnUnknownWorkingRevisionFails() {
		assertThatThrownBy(() -> this.service.getOwnDraft(UUID.randomUUID(), this.editorA))
			.isInstanceOf(WorkingRevisionNotFoundException.class);
	}

}
