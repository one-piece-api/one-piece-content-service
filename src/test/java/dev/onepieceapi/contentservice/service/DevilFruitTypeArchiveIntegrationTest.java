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

import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.ARCHIVE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.RECOVER;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * A publisher setting a version ready to publish aside (UF-CNT-16) and bringing it back
 * (UF-CNT-17) against a real PostgreSQL (Testcontainers).
 * <p>
 * Seeded for every test:
 * <ul>
 * <li>Logia - v1 by nami online, v2 by chopper ready to publish</li>
 * <li>Zoan - v1 by nami archived, nothing online</li>
 * <li>Kodai - v1 by nami archived, v2 by chopper a draft</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitTypeArchiveIntegrationTest {

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
		version(this.logia, 2, READY_TO_PUBLISH, this.chopper, "Logia Prime");
		this.zoan = content();
		version(this.zoan, 1, ARCHIVED, this.nami, "Zoan");
		this.kodai = content();
		version(this.kodai, 1, ARCHIVED, this.nami, "Kodai");
		version(this.kodai, 2, DRAFT, this.chopper, "Kodai");
		stored();
	}

	@Test
	void aPublisherArchivesAVersionReadyToPublishWithoutTouchingTheOnlineOne() {
		var answer = this.service.archive(PUBLISHER, this.vivi, this.logia, 2);
		stored();

		Version<DevilFruitType> archived = this.service.getVersion(PUBLISHER, this.vivi, this.logia, 2).version();
		assertThat(archived.status()).isEqualTo(ARCHIVED);
		assertThat(archived.updatedAt()).isEqualTo(NOW);
		assertThat(answer.version().status()).isEqualTo(ARCHIVED);
		assertThat(answer.allowedActions()).containsExactly(RECOVER);
		Version<DevilFruitType> online = this.service.getVersion(PUBLISHER, this.vivi, this.logia, 1).version();
		assertThat(online.status()).isEqualTo(PUBLISHED);
		assertThat(online.updatedAt()).isEqualTo(EARLIER);
	}

	@Test
	void archivingIsRecordedOnTheVersionWithoutDetail() {
		this.service.archive(PUBLISHER, this.vivi, this.logia, 2);
		stored();

		var events = this.service.events(PUBLISHER, this.logia, 2);
		assertThat(events).extracting(VersionEvent::action).containsExactly("VERSION_ARCHIVED");
		assertThat(events).extracting(VersionEvent::actor).containsExactly(this.vivi);
		assertThat(events).extracting(VersionEvent::detail).containsOnlyNulls();
	}

	@Test
	void archivingFreesTheContentForANewDraft() {
		this.service.archive(PUBLISHER, this.vivi, this.logia, 2);
		stored();

		var opened = this.service.openNewVersion(EDITOR, this.nami, this.logia, 2);
		assertThat(opened.version().number()).isEqualTo(3);
		assertThat(opened.version().basedOn()).isEqualTo(2);
	}

	@Test
	void onlyAVersionReadyToPublishIsArchived() {
		assertThatThrownBy(() -> this.service.archive(PUBLISHER, this.vivi, this.logia, 1))
			.isInstanceOf(VersionActionConflictException.class);
		assertThatThrownBy(() -> this.service.archive(PUBLISHER, this.vivi, this.zoan, 1))
			.isInstanceOf(VersionActionConflictException.class);
	}

	@Test
	void editorsAndReviewersSeeAVersionReadyToPublishButMayNotArchiveIt() {
		assertThatThrownBy(() -> this.service.archive(EDITOR, this.chopper, this.logia, 2))
			.isInstanceOf(VersionActionForbiddenException.class);
		assertThatThrownBy(() -> this.service.archive(REVIEWER, this.zoro, this.logia, 2))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@Test
	void aPublisherRecoversAnArchivedVersionAmongThoseReadyToPublish() {
		var answer = this.service.recover(PUBLISHER, this.vivi, this.zoan, 1);
		stored();

		Version<DevilFruitType> recovered = this.service.getVersion(PUBLISHER, this.vivi, this.zoan, 1).version();
		assertThat(recovered.status()).isEqualTo(READY_TO_PUBLISH);
		assertThat(recovered.updatedAt()).isEqualTo(NOW);
		assertThat(answer.allowedActions()).containsExactly(PUBLISH, ARCHIVE);
		var events = this.service.events(PUBLISHER, this.zoan, 1);
		assertThat(events).extracting(VersionEvent::action).containsExactly("VERSION_RECOVERED");
		assertThat(events).extracting(VersionEvent::actor).containsExactly(this.vivi);
	}

	@Test
	void aRecoveredVersionIsTheContentsOpenOne() {
		this.service.recover(PUBLISHER, this.vivi, this.zoan, 1);
		stored();

		assertThatThrownBy(() -> this.service.openNewVersion(EDITOR, this.chopper, this.zoan, 1))
			.isInstanceOf(VersionActionConflictException.class);
	}

	@Test
	void recoveringIsRefusedWhileALaterVersionIsOpen() {
		assertThatThrownBy(() -> this.service.recover(PUBLISHER, this.vivi, this.kodai, 1))
			.isInstanceOf(VersionActionConflictException.class);
		assertThat(this.service.getVersion(PUBLISHER, this.vivi, this.kodai, 1).allowedActions()).isEmpty();
	}

	@Test
	void recoveringIsAllowedAgainOnceTheOpenVersionIsDiscarded() {
		this.service.delete(EDITOR, this.chopper, this.kodai, 2);
		stored();

		var answer = this.service.recover(PUBLISHER, this.vivi, this.kodai, 1);
		assertThat(answer.version().status()).isEqualTo(READY_TO_PUBLISH);
	}

	@Test
	void onlyAnArchivedVersionIsRecovered() {
		assertThatThrownBy(() -> this.service.recover(PUBLISHER, this.vivi, this.logia, 1))
			.isInstanceOf(VersionActionConflictException.class);
	}

	@Test
	void editorsAndReviewersSeeAnArchivedVersionButMayNotRecoverIt() {
		assertThatThrownBy(() -> this.service.recover(EDITOR, this.chopper, this.zoan, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
		assertThatThrownBy(() -> this.service.recover(REVIEWER, this.zoro, this.zoan, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@Test
	void aRecoveredVersionGoesOnlineSupersedingTheOneThere() {
		this.service.archive(PUBLISHER, this.vivi, this.logia, 2);
		stored();
		this.service.recover(PUBLISHER, this.vivi, this.logia, 2);
		stored();
		this.service.publish(PUBLISHER, this.vivi, this.logia, 2);
		stored();

		assertThat(this.service.getVersion(PUBLISHER, this.vivi, this.logia, 2).version().status())
			.isEqualTo(PUBLISHED);
		assertThat(this.service.getVersion(PUBLISHER, this.vivi, this.logia, 1).version().status())
			.isEqualTo(SUPERSEDED);
	}

	@Test
	void aVersionThatDoesNotExistIsNotFound() {
		assertThatThrownBy(() -> this.service.archive(PUBLISHER, this.vivi, this.logia, 7))
			.isInstanceOf(VersionNotFoundException.class);
		assertThatThrownBy(() -> this.service.recover(PUBLISHER, this.vivi, UUID.randomUUID(), 1))
			.isInstanceOf(VersionNotFoundException.class);
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
		var body = new DevilFruitType(romaji, Map.of("it", new DevilFruitTypeTranslation(romaji + " IT", "descrizione"),
				"en", new DevilFruitTypeTranslation(romaji + " EN", "description")));
		version.setRomaji(body.romaji());
		body.translations()
			.forEach((language, translation) -> version.getTranslations()
				.put(language, new TranslationEmbeddable(translation.name(), translation.description())));
		this.versionRepository.save(version);
	}

}
