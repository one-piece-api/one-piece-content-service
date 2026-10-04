package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionEvent;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.exception.VersionActionConflictException;
import dev.onepieceapi.contentservice.service.exception.VersionActionForbiddenException;
import dev.onepieceapi.contentservice.service.exception.VersionNotFoundException;
import dev.onepieceapi.contentservice.service.validation.DevilFruitTypeValidator;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.DELETE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.EDIT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.SUBMIT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * An editor opening the next version of a content from one of its closed versions
 * (UF-CNT-08) against a real PostgreSQL (Testcontainers).
 * <p>
 * Seeded for every test:
 * <ul>
 * <li>Logia - v1 by nami online</li>
 * <li>Paramecia - v1 by nami superseded, v2 by chopper online</li>
 * <li>Zoan - v1 by nami archived</li>
 * <li>Kodai - v1 by nami online, v2 by nami in review</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitTypeNewVersionIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> REVIEWER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH,
			Permission.CONTENT_RETIRE);

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	private final User zoro = new User(UUID.randomUUID(), "zoro", "zoro@onepiece.local");

	private final User vivi = new User(UUID.randomUUID(), "vivi", "vivi@onepiece.local");

	@Autowired
	private DevilFruitTypeVersionRepository versionRepository;

	@Autowired
	private ContentVersionRepository contentVersionRepository;

	@Autowired
	private ContentRepository contentRepository;

	@Autowired
	private LanguageRepository languageRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private TestEntityManager entityManager;

	private DevilFruitTypeService service;

	private UUID logia;

	private UUID paramecia;

	private UUID zoan;

	private UUID kodai;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		var validator = new DevilFruitTypeValidator(this.versionRepository, this.languageRepository);
		this.service = new DevilFruitTypeService(this.versionRepository, this.contentVersionRepository,
				this.contentRepository, validator, auditLogService, clock);

		this.logia = content();
		version(this.logia, 1, PUBLISHED, this.nami, "Logia");
		this.paramecia = content();
		version(this.paramecia, 1, SUPERSEDED, this.nami, "Paramecia");
		version(this.paramecia, 2, PUBLISHED, this.chopper, "Paramecia Prime");
		this.zoan = content();
		version(this.zoan, 1, ARCHIVED, this.nami, "Zoan");
		this.kodai = content();
		version(this.kodai, 1, PUBLISHED, this.nami, "Kodai");
		version(this.kodai, 2, IN_REVIEW, this.nami, "Kodai");
		stored();
	}

	@Test
	void anyEditorOpensTheNextVersionAsTheirOwnDraftSayingWhatTheBaseSays() {
		var answer = this.service.openNewVersion(EDITOR, this.chopper, this.logia, 1);
		stored();

		Version<DevilFruitType> opened = this.service.getVersion(EDITOR, this.chopper, this.logia, 2).version();
		Version<DevilFruitType> base = this.service.getVersion(EDITOR, this.chopper, this.logia, 1).version();
		assertThat(opened.number()).isEqualTo(2);
		assertThat(opened.basedOn()).isEqualTo(1);
		assertThat(opened.status()).isEqualTo(DRAFT);
		assertThat(opened.author()).isEqualTo(this.chopper);
		assertThat(opened.body()).isEqualTo(base.body());
		assertThat(opened.createdAt()).isEqualTo(NOW);
		assertThat(opened.updatedAt()).isEqualTo(NOW);
		assertThat(answer.version().number()).isEqualTo(2);
		assertThat(answer.allowedActions()).containsExactlyInAnyOrder(EDIT, DELETE, SUBMIT);
	}

	@Test
	void theOnlineVersionIsUntouched() {
		this.service.openNewVersion(EDITOR, this.chopper, this.logia, 1);
		stored();

		var content = this.service.get(PUBLISHER, this.vivi, this.logia);
		assertThat(content.onlineVersionNumber()).contains(1);
		Version<DevilFruitType> online = this.service.getVersion(PUBLISHER, this.vivi, this.logia, 1).version();
		assertThat(online.status()).isEqualTo(PUBLISHED);
		assertThat(online.updatedAt()).isEqualTo(EARLIER);
		assertThat(this.service.events(PUBLISHER, this.logia, 1)).isEmpty();
	}

	@Test
	void anOlderVersionThanTheOnlineOneCanBeTheBase() {
		this.service.openNewVersion(EDITOR, this.nami, this.paramecia, 1);
		stored();

		Version<DevilFruitType> opened = this.service.getVersion(EDITOR, this.nami, this.paramecia, 3).version();
		assertThat(opened.basedOn()).isEqualTo(1);
		assertThat(opened.body().romaji()).isEqualTo("Paramecia");
		assertThat(this.service.get(EDITOR, this.nami, this.paramecia).onlineVersionNumber()).contains(2);
	}

	@Test
	void anArchivedVersionCanBeTheBase() {
		this.service.openNewVersion(EDITOR, this.chopper, this.zoan, 1);
		stored();

		assertThat(this.service.getVersion(EDITOR, this.chopper, this.zoan, 2).version().basedOn()).isEqualTo(1);
		assertThat(this.service.getVersion(EDITOR, this.chopper, this.zoan, 1).version().status()).isEqualTo(ARCHIVED);
	}

	@Test
	void openingIsRecordedOnTheNewVersionWithItsBase() {
		this.service.openNewVersion(EDITOR, this.chopper, this.paramecia, 1);
		stored();

		var events = this.service.events(EDITOR, this.paramecia, 3);
		assertThat(events).extracting(VersionEvent::action).containsExactly("VERSION_CREATED");
		assertThat(events).extracting(VersionEvent::actor).containsExactly(this.chopper);
		assertThat(events).extracting(VersionEvent::detail).containsExactly("1");
	}

	@Test
	void refusedWhileTheContentHasAnOpenVersion() {
		assertThatThrownBy(() -> this.service.openNewVersion(EDITOR, this.chopper, this.kodai, 1))
			.isInstanceOf(VersionActionConflictException.class);
	}

	@Test
	void refusedASecondTimeOnceTheFirstNewVersionIsOpen() {
		this.service.openNewVersion(EDITOR, this.chopper, this.logia, 1);
		stored();

		assertThatThrownBy(() -> this.service.openNewVersion(EDITOR, this.nami, this.logia, 1))
			.isInstanceOf(VersionActionConflictException.class);
	}

	@Test
	void anOpenVersionIsNeverABase() {
		assertThatThrownBy(() -> this.service.openNewVersion(EDITOR, this.chopper, this.kodai, 2))
			.isInstanceOf(VersionActionConflictException.class);
	}

	@Test
	void reviewersAndPublishersSeeTheBaseButMayNotOpenAVersion() {
		assertThatThrownBy(() -> this.service.openNewVersion(REVIEWER, this.zoro, this.logia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
		assertThatThrownBy(() -> this.service.openNewVersion(PUBLISHER, this.vivi, this.logia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@Test
	void aBaseThatDoesNotExistIsNotFound() {
		assertThatThrownBy(() -> this.service.openNewVersion(EDITOR, this.chopper, this.logia, 7))
			.isInstanceOf(VersionNotFoundException.class);
		assertThatThrownBy(() -> this.service.openNewVersion(EDITOR, this.chopper, UUID.randomUUID(), 1))
			.isInstanceOf(VersionNotFoundException.class);
	}

	@Test
	void discardingTheNewVersionFreesItsNumberAndTheContent() {
		this.service.openNewVersion(EDITOR, this.chopper, this.logia, 1);
		stored();
		this.service.delete(EDITOR, this.chopper, this.logia, 2);
		stored();

		var reopened = this.service.openNewVersion(EDITOR, this.nami, this.logia, 1);
		assertThat(reopened.version().number()).isEqualTo(2);
		assertThat(reopened.version().author()).isEqualTo(this.nami);
	}

	/** What was done so far is written and read again from the database. */
	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

	private UUID content() {
		return this.entityManager.persist(new ContentEntity(UUID.randomUUID(), EntityType.DEVIL_FRUIT_TYPE, EARLIER))
			.getId();
	}

	/**
	 * Seeds a version of a content, complete in both languages of the catalog; any but
	 * the first is based on the one before.
	 */
	private void version(UUID contentId, int number, VersionStatus status, User author, String romaji) {
		var workflow = ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(number)
			.basedOnNumber(number == 1 ? null : number - 1)
			.author(UserMapper.toEmbeddable(author))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build();
		var version = new DevilFruitTypeVersionEntity(workflow);
		var body = new DevilFruitType(romaji,
				Map.of("it", new DevilFruitTypeTranslation(romaji + " IT", "descrizione", "vantaggi", "svantaggi"),
						"en",
						new DevilFruitTypeTranslation(romaji + " EN", "description", "advantages", "disadvantages")));
		version.setRomaji(body.romaji());
		body.translations()
			.forEach((language, translation) -> version.getTranslations()
				.put(language, new TranslationEmbeddable(translation.name(), translation.description(),
						translation.advantages(), translation.disadvantages())));
		this.versionRepository.save(version);
	}

}
