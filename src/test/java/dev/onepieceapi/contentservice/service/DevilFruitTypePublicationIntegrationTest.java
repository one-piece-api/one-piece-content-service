package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.OPEN_NEW_VERSION;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.RESTORE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionAction.RETIRE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * A publisher putting a version online (UF-CNT-07) against a real PostgreSQL
 * (Testcontainers): the version online until then is superseded in the same transaction,
 * which the one-online-version index of {@code V2} and the optimistic lock of {@code V4}
 * hold to.
 * <p>
 * Seeded for every test:
 * <ul>
 * <li>Logia, by nami - v1 ready to publish, never online</li>
 * <li>Paramecia - v1 by nami online, v2 by chopper based on it, ready to publish</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class DevilFruitTypePublicationIntegrationTest {

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

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		var validator = new DevilFruitTypeValidator(this.versionRepository, this.languageRepository);
		this.service = new DevilFruitTypeService(this.versionRepository, this.contentVersionRepository,
				this.contentRepository, validator, auditLogService, clock);

		this.logia = content();
		version(this.logia, 1, READY_TO_PUBLISH, this.nami, "Logia");
		this.paramecia = content();
		version(this.paramecia, 1, PUBLISHED, this.nami, "Paramecia");
		version(this.paramecia, 2, READY_TO_PUBLISH, this.chopper, "Paramecia");
		stored();
	}

	@Test
	void aPublishedVersionIsOnlineAndItsContentFreeForANewDraft() {
		var answer = this.service.publish(PUBLISHER, this.vivi, this.logia, 1);
		stored();

		var version = this.service.getVersion(PUBLISHER, this.vivi, this.logia, 1).version();
		assertThat(version.status()).isEqualTo(PUBLISHED);
		assertThat(version.updatedAt()).isEqualTo(NOW);
		assertThat(answer.allowedActions()).containsExactly(RETIRE);
		assertThat(this.service.getVersion(EDITOR, this.chopper, this.logia, 1).allowedActions())
			.containsExactly(OPEN_NEW_VERSION);
	}

	@Test
	void theVersionOnlineUntilThenIsSupersededAndStaysInTheHistory() {
		this.service.publish(PUBLISHER, this.vivi, this.paramecia, 2);
		stored();

		var content = this.service.get(PUBLISHER, this.vivi, this.paramecia);
		assertThat(content.onlineVersionNumber()).contains(2);
		var replaced = this.service.getVersion(PUBLISHER, this.vivi, this.paramecia, 1);
		assertThat(replaced.version().status()).isEqualTo(SUPERSEDED);
		assertThat(replaced.version().updatedAt()).isEqualTo(NOW);
		assertThat(replaced.allowedActions()).containsExactly(RESTORE);
	}

	@Test
	void publishingIsRecordedOnTheVersionAndTheSupersessionOnTheOneItReplaces() {
		this.service.publish(PUBLISHER, this.vivi, this.paramecia, 2);
		stored();

		List<VersionEvent> published = this.service.events(PUBLISHER, this.paramecia, 2);
		assertThat(published).extracting(VersionEvent::action).containsExactly("VERSION_PUBLISHED");
		assertThat(published).extracting(VersionEvent::actor).containsExactly(this.vivi);
		List<VersionEvent> superseded = this.service.events(PUBLISHER, this.paramecia, 1);
		assertThat(superseded).extracting(VersionEvent::action).containsExactly("VERSION_SUPERSEDED");
		assertThat(superseded).extracting(VersionEvent::actor).containsExactly(this.vivi);
		assertThat(superseded).extracting(VersionEvent::detail).containsExactly("2");
	}

	@Test
	void aFirstPublicationSupersedesNothing() {
		this.service.publish(PUBLISHER, this.vivi, this.logia, 1);
		stored();

		assertThat(this.service.events(PUBLISHER, this.logia, 1)).extracting(VersionEvent::action)
			.containsExactly("VERSION_PUBLISHED");
	}

	@Test
	void editorsAndReviewersSeeAReadyVersionButMayNotPublishIt() {
		assertThat(this.service.getVersion(EDITOR, this.nami, this.logia, 1).allowedActions()).isEmpty();
		assertThatThrownBy(() -> this.service.publish(EDITOR, this.nami, this.logia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
		assertThatThrownBy(() -> this.service.publish(REVIEWER, this.zoro, this.logia, 1))
			.isInstanceOf(VersionActionForbiddenException.class);
	}

	@Test
	void onlyAVersionReadyToPublishIsPublished() {
		assertThatThrownBy(() -> this.service.publish(PUBLISHER, this.vivi, this.paramecia, 1))
			.isInstanceOf(VersionActionConflictException.class);
	}

	@Test
	void aDraftIsNotEvenSeenByAPublisher() {
		UUID draft = content();
		version(draft, 1, DRAFT, this.nami, "Kodai");
		stored();

		assertThatThrownBy(() -> this.service.publish(PUBLISHER, this.vivi, draft, 1))
			.isInstanceOf(VersionNotFoundException.class);
	}

	@Test
	void aPublicationWrittenFromAnOutdatedReadFailsInsteadOfOverwritingTheOtherOne() {
		// vivi reads both versions: v1 online, v2 ready...
		this.versionRepository.findOnline(this.paramecia).orElseThrow();
		this.versionRepository.findVisible(this.paramecia, 2, Set.of(READY_TO_PUBLISH)).orElseThrow();
		// ...another publisher changes them and commits in the meantime, bumping the
		// locks...
		this.entityManager.getEntityManager()
			.createNativeQuery("update content_version set lock_version = lock_version + 1 where content_id = ?1")
			.setParameter(1, this.paramecia)
			.executeUpdate();

		// ...so vivi's publication, decided on what she read, cannot be written.
		assertThatThrownBy(() -> this.service.publish(PUBLISHER, this.vivi, this.paramecia, 2))
			.isInstanceOf(OptimisticLockingFailureException.class);
	}

	@Test
	void publishingGivesTheContentTheSlugOfItsRomaji() {
		this.service.publish(PUBLISHER, this.vivi, this.logia, 1);
		stored();

		assertThat(slugsOf(this.logia)).containsExactly("logia");
	}

	@Test
	void aNewRomajiAddsItsSlugAndTheOldOneStaysInTheHistory() {
		UUID chojin = content();
		version(chojin, 1, PUBLISHED, this.nami, "Chojin");
		slug(chojin, "chojin");
		version(chojin, 2, READY_TO_PUBLISH, this.nami, "Chōjin-kei");
		stored();

		this.service.publish(PUBLISHER, this.vivi, chojin, 2);
		stored();

		assertThat(slugsOf(chojin)).containsExactlyInAnyOrder("chojin", "chojin-kei");
	}

	@Test
	void aContentPublishedAgainUnderTheSameRomajiKeepsOneSlug() {
		slug(this.paramecia, "paramecia");

		this.service.publish(PUBLISHER, this.vivi, this.paramecia, 2);
		stored();

		assertThat(slugsOf(this.paramecia)).containsExactly("paramecia");
	}

	@Test
	void aSlugTakenMeanwhileByAnotherContentFailsThePublication() {
		// Two drafts saved at the same instant both passed the check: the other one went
		// online first.
		slug(this.paramecia, "logia");

		assertThatThrownBy(() -> this.service.publish(PUBLISHER, this.vivi, this.logia, 1))
			.isInstanceOf(VersionActionConflictException.class);
	}

	/** What was done so far is written and read again from the database. */
	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

	private void slug(UUID contentId, String slug) {
		this.entityManager.getEntityManager()
			.createNativeQuery("insert into content_slug (entity_type, slug, content_id, assigned_at)"
					+ " values ('DEVIL_FRUIT_TYPE', ?, ?, ?)")
			.setParameter(1, slug)
			.setParameter(2, contentId)
			.setParameter(3, EARLIER)
			.executeUpdate();
	}

	@SuppressWarnings("unchecked")
	private List<String> slugsOf(UUID contentId) {
		return this.entityManager.getEntityManager()
			.createNativeQuery("select slug from content_slug where content_id = ?", String.class)
			.setParameter(1, contentId)
			.getResultList();
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
