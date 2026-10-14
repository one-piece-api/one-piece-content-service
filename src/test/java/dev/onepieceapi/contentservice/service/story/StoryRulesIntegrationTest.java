package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.BlockedAction;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ArcVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.SagaVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.validation.ArcValidator;
import dev.onepieceapi.contentservice.service.validation.SagaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.RETIRED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * What a saga and its arcs owe each other (implementation plan of the story structure,
 * S5) against a real PostgreSQL (Testcontainers), one test per row of the plan's table:
 * an arc goes online only while its saga is; a saga is not retired while arcs are online
 * in it; nothing else is asked of the other side. Whoever tries anyway is refused with
 * the reason, even an administrator, and the reason is listed with the version before
 * anyone tries. The two sides running at the same instant are in
 * {@link StoryRulesRaceIntegrationTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class StoryRulesIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

	private static final Instant EARLIER = NOW.minus(Duration.ofDays(3));

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final Set<Permission> REVIEWER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	private static final Set<Permission> RETIRER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_RETIRE);

	private static final Set<Permission> ADMIN = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE,
			Permission.CONTENT_REVIEW, Permission.CONTENT_PUBLISH, Permission.CONTENT_RETIRE, Permission.CONTENT_ADMIN);

	private final User zoro = new User(UUID.randomUUID(), "zoro", "zoro@onepiece.local");

	private final User vivi = new User(UUID.randomUUID(), "vivi", "vivi@onepiece.local");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Autowired
	private SagaVersionRepository sagaRepository;

	@Autowired
	private ArcVersionRepository arcRepository;

	@Autowired
	private ChapterVersionRepository chapterRepository;

	@Autowired
	private EpisodeVersionRepository episodeRepository;

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

	private SagaService sagas;

	private ArcService arcs;

	private int numbers;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		this.sagas = new SagaService(this.sagaRepository, this.contentVersionRepository, this.contentRepository,
				new SagaValidator(this.sagaRepository, this.languageRepository),
				new SagaRules(this.contentRepository, this.arcRepository, new RulesProperties(5)), auditLogService,
				clock);
		this.arcs = new ArcService(this.arcRepository, this.contentVersionRepository, this.contentRepository,
				new ArcValidator(this.arcRepository, this.languageRepository, this.sagaRepository),
				new ArcRules(this.contentRepository, this.sagaRepository, this.chapterRepository,
						this.episodeRepository, new RulesProperties(5)),
				auditLogService, clock);
	}

	@Test
	void anArcOfASagaNoLongerOnlineCanStillBeSubmittedReviewedAndSetAside() {
		UUID saga = sagaContent(RETIRED);
		UUID arc = arcContent(saga, DRAFT);
		stored();

		assertThat(this.arcs.submit(EDITOR, this.chopper, arc, 1).version().status())
			.isEqualTo(VersionStatus.IN_REVIEW);
		this.arcs.claim(REVIEWER, this.zoro, arc, 1);
		assertThat(this.arcs.approve(REVIEWER, this.zoro, arc, 1).version().status()).isEqualTo(READY_TO_PUBLISH);
		assertThat(this.arcs.archive(PUBLISHER, this.vivi, arc, 1).version().status()).isEqualTo(ARCHIVED);
		assertThat(this.arcs.recover(PUBLISHER, this.vivi, arc, 1).version().status()).isEqualTo(READY_TO_PUBLISH);
	}

	@Test
	void anArcGoesOnlineWhileItsSagaIs() {
		UUID saga = sagaContent(PUBLISHED);
		UUID arc = arcContent(saga, READY_TO_PUBLISH);
		stored();

		assertThat(this.arcs.publish(PUBLISHER, this.vivi, arc, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@ParameterizedTest
	@EnumSource(names = { "READY_TO_PUBLISH", "ARCHIVED", "RETIRED" })
	void anArcDoesNotGoOnlineWhileItsSagaIsNot(VersionStatus sagaStatus) {
		UUID saga = sagaContent(sagaStatus);
		int sagaNumber = this.numbers;
		UUID arc = arcContent(saga, READY_TO_PUBLISH);
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.arcs.publish(PUBLISHER, this.vivi, arc, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail")).isEqualTo(Map.of("sagaId", saga, "sagaNumber", sagaNumber));
		stored();
		assertThat(statusOfArc(arc, 1)).isEqualTo(READY_TO_PUBLISH);
	}

	@Test
	void anArcTakenOfflineIsRestoredWhileItsSagaIsOnlineAndNotBefore() {
		UUID offline = sagaContent(RETIRED);
		UUID online = sagaContent(PUBLISHED);
		UUID early = arcContent(offline, RETIRED);
		UUID fine = arcContent(online, RETIRED);
		stored();

		assertThatThrownBy(() -> this.arcs.restore(PUBLISHER, this.vivi, early, 1))
			.isInstanceOf(VersionActionBlockedException.class);
		assertThat(this.arcs.restore(PUBLISHER, this.vivi, fine, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void theVersionSaysBeforeAnyoneTriesWhichOfItsActionsAreBlocked() {
		UUID offline = sagaContent(RETIRED);
		int offlineNumber = this.numbers;
		UUID online = sagaContent(PUBLISHED);
		UUID blocked = arcContent(offline, READY_TO_PUBLISH);
		UUID free = arcContent(online, READY_TO_PUBLISH);
		stored();

		var withReason = this.arcs.getVersion(PUBLISHER, this.vivi, blocked, 1);
		var without = this.arcs.getVersion(PUBLISHER, this.vivi, free, 1);

		assertThat(withReason.allowedActions()).contains(VersionAction.PUBLISH, VersionAction.ARCHIVE);
		assertThat(withReason.blockedActions()).containsExactly(new BlockedAction(VersionAction.PUBLISH,
				BlockReason.RELATED_NOT_ONLINE, Map.of("sagaId", offline, "sagaNumber", offlineNumber)));
		assertThat(without.blockedActions()).isEmpty();
	}

	@Test
	void aSagaIsNotRetiredWhileArcsAreOnlineInItAndTheRefusalNamesSome() {
		UUID saga = sagaContent(PUBLISHED);
		List<UUID> arcIds = IntStream.range(0, 6).mapToObj(index -> arcContent(saga, PUBLISHED)).toList();
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.sagas.retire(RETIRER, this.vivi, saga, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.ONLINE_ITEMS_LINKED);
		@SuppressWarnings("unchecked")
		var detail = (Map<String, Object>) refused.getDetails().get("detail");
		assertThat(detail).containsEntry("count", 6L);
		@SuppressWarnings("unchecked")
		var named = (List<Map<String, Object>>) detail.get("arcs");
		assertThat(named).hasSize(5);
		assertThat(named).extracting(arc -> arc.get("id")).containsExactlyElementsOf(arcIds.subList(0, 5));
		assertThat(named.getFirst()).containsOnlyKeys("id", "number");
		assertThat(named).extracting(arc -> arc.get("number")).isSorted();
		stored();
		assertThat(statusOfSaga(saga, 1)).isEqualTo(PUBLISHED);
	}

	@Test
	void theVersionOfASagaListsTheRetirementItCannotDo() {
		UUID saga = sagaContent(PUBLISHED);
		arcContent(saga, PUBLISHED);
		UUID alone = sagaContent(PUBLISHED);
		stored();

		var withArc = this.sagas.getVersion(RETIRER, this.vivi, saga, 1);
		var withoutArc = this.sagas.getVersion(RETIRER, this.vivi, alone, 1);

		assertThat(withArc.allowedActions()).contains(VersionAction.RETIRE);
		assertThat(withArc.blockedActions()).extracting(BlockedAction::action, BlockedAction::reason)
			.containsExactly(tuple(VersionAction.RETIRE, BlockReason.ONLINE_ITEMS_LINKED));
		assertThat(withoutArc.blockedActions()).isEmpty();
	}

	@Test
	void arcsNotOnlineInTheSagaDoNotKeepItOnline() {
		UUID saga = sagaContent(PUBLISHED);
		UUID other = sagaContent(PUBLISHED);
		for (VersionStatus status : List.of(DRAFT, READY_TO_PUBLISH, ARCHIVED, RETIRED, SUPERSEDED)) {
			arcContent(saga, status);
		}
		arcContent(other, PUBLISHED);
		stored();

		assertThat(this.sagas.retire(RETIRER, this.vivi, saga, 1).version().status()).isEqualTo(RETIRED);
	}

	@Test
	void onlyTheOnlineVersionOfAnArcSaysWhichSagaItKeepsOnline() {
		UUID saga = sagaContent(PUBLISHED);
		UUID other = sagaContent(PUBLISHED);
		UUID arc = arcContent(saga, SUPERSEDED);
		arcVersion(arc, 2, PUBLISHED, complete(this.numbers + 100, other));
		stored();

		assertThat(this.sagas.retire(RETIRER, this.vivi, saga, 1).version().status()).isEqualTo(RETIRED);
	}

	@Test
	void aNewOnlineVersionOfTheSagaChangesNothingForItsArcs() {
		UUID saga = sagaContent(PUBLISHED);
		sagaVersion(saga, 2, READY_TO_PUBLISH, complete(this.numbers + 100));
		UUID arc = arcContent(saga, PUBLISHED);
		stored();

		this.sagas.publish(PUBLISHER, this.vivi, saga, 2);
		stored();

		assertThat(statusOfArc(arc, 1)).isEqualTo(PUBLISHED);
		assertThatThrownBy(() -> this.sagas.retire(RETIRER, this.vivi, saga, 2))
			.isInstanceOf(VersionActionBlockedException.class);
	}

	@Test
	void anAdministratorIsRefusedToo() {
		UUID offline = sagaContent(RETIRED);
		UUID early = arcContent(offline, READY_TO_PUBLISH);
		UUID online = sagaContent(PUBLISHED);
		arcContent(online, PUBLISHED);
		stored();

		assertThatThrownBy(() -> this.arcs.publish(ADMIN, this.vivi, early, 1))
			.isInstanceOf(VersionActionBlockedException.class);
		assertThatThrownBy(() -> this.sagas.retire(ADMIN, this.vivi, online, 1))
			.isInstanceOf(VersionActionBlockedException.class);
	}

	@Test
	void anAdministratorIsRefusedTheRestoreOfAnArcToo() {
		UUID offline = sagaContent(RETIRED);
		UUID early = arcContent(offline, RETIRED);
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.arcs.restore(ADMIN, this.vivi, early, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
	}

	private VersionStatus statusOfArc(UUID contentId, int number) {
		return this.arcs.getVersion(ADMIN, this.vivi, contentId, number).version().status();
	}

	private VersionStatus statusOfSaga(UUID contentId, int number) {
		return this.sagas.getVersion(ADMIN, this.vivi, contentId, number).version().status();
	}

	/** Complete in both languages of the catalog; the number is its own title. */
	private static Saga complete(int number) {
		return new Saga(number, Map.of("it", new StoryTranslation("Saga it " + number, "Descrizione"), "en",
				new StoryTranslation("Saga " + number, "Description")));
	}

	private static Arc complete(int number, UUID saga) {
		return new Arc(number, saga, Map.of("it", new StoryTranslation("Arco it " + number, "Descrizione"), "en",
				new StoryTranslation("Arc " + number, "Description")));
	}

	/** What was done so far is written and read again from the database. */
	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

	private UUID content(EntityType entityType) {
		return this.entityManager.persist(new ContentEntity(UUID.randomUUID(), entityType, EARLIER)).getId();
	}

	/** A saga with the next free number and a single version in this status. */
	private UUID sagaContent(VersionStatus status) {
		UUID contentId = content(EntityType.SAGA);
		sagaVersion(contentId, 1, status, complete(++this.numbers));
		return contentId;
	}

	private void sagaVersion(UUID contentId, int number, VersionStatus status, Saga body) {
		var version = new SagaVersionEntity(workflow(contentId, number, status));
		StoryVersionMapper.rewrite(version, body, EARLIER);
		this.sagaRepository.save(version);
	}

	/**
	 * An arc of this saga, numbered after the sagas, with a single version in this
	 * status.
	 */
	private UUID arcContent(UUID saga, VersionStatus status) {
		UUID contentId = content(EntityType.ARC);
		arcVersion(contentId, 1, status, complete(1000 + ++this.numbers, saga));
		return contentId;
	}

	private void arcVersion(UUID contentId, int number, VersionStatus status, Arc body) {
		var version = new ArcVersionEntity(workflow(contentId, number, status));
		StoryVersionMapper.rewrite(version, body, EARLIER);
		this.arcRepository.save(version);
	}

	private ContentVersionEntity workflow(UUID contentId, int number, VersionStatus status) {
		return ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(number)
			.basedOnNumber(number == 1 ? null : number - 1)
			.author(UserMapper.toEmbeddable(this.chopper))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build();
	}

}
