package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ArcVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ChapterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.SagaVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.validation.ArcValidator;
import dev.onepieceapi.contentservice.service.validation.ChapterValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * A chapter going online and its arc being retired at the same instant, each in its own
 * transaction against a real PostgreSQL (Testcontainers) - the race of
 * {@link StoryRulesRaceIntegrationTest} for the second relation of the story structure
 * (implementation plan, S5): the side that comes first is held right after it read what
 * its rule needs, and the other is started; it must wait for the first to finish, then
 * find what the first wrote and be refused. Not run inside a test transaction: each
 * caller commits, as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StoryItemsRulesRaceIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	private static final Set<Permission> RETIRER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_RETIRE);

	private static final long WAIT_SECONDS = 10;

	/** Long enough for a caller that is not waiting on a lock to have finished. */
	private static final long SETTLE_MILLIS = 700;

	/** Numbers of their own, so runs and tests never collide. */
	private static final AtomicInteger NUMBERS = new AtomicInteger(9000);

	private final User vivi = new User(UUID.randomUUID(), "vivi", "vivi@onepiece.local");

	private final User luffy = new User(UUID.randomUUID(), "luffy", "luffy@onepiece.local");

	@Autowired
	private SagaVersionRepository sagaRepository;

	@Autowired
	private ArcVersionRepository arcRepository;

	@Autowired
	private VolumeVersionRepository volumeRepository;

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
	private PlatformTransactionManager transactionManager;

	@Test
	void aChapterPublishedWhileItsArcIsBeingRetiredWaitsAndIsRefusedOnceTheArcIsOffline() throws Exception {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID saga = transaction.execute(status -> sagaContent());
		UUID arc = transaction.execute(status -> arcContent(saga, PUBLISHED));
		UUID chapter = transaction.execute(status -> chapterContent(arc, READY_TO_PUBLISH));
		var retireCountedChapters = new CountDownLatch(1);
		var letRetireGo = new CountDownLatch(1);
		ChapterVersionRepository holdingCount = mock(ChapterVersionRepository.class,
				delegatesTo(this.chapterRepository));
		doAnswer(invocation -> {
			long count = this.chapterRepository.countOnlineInArc(arc);
			retireCountedChapters.countDown();
			letRetireGo.await(WAIT_SECONDS, TimeUnit.SECONDS);
			return count;
		}).when(holdingCount).countOnlineInArc(arc);
		var arcs = arcService(holdingCount);
		var chapters = chapterService(this.arcRepository);

		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<?> retire = executor
				.submit(() -> transaction.execute(status -> arcs.retire(RETIRER, this.vivi, arc, 1)));
			assertThat(retireCountedChapters.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			Future<?> publish = executor
				.submit(() -> transaction.execute(status -> chapters.publish(PUBLISHER, this.luffy, chapter, 1)));

			Thread.sleep(SETTLE_MILLIS);
			assertThat(publish.isDone()).as("the publication waits for the retirement").isFalse();
			letRetireGo.countDown();

			assertThat(retire.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
			assertThatThrownBy(() -> publish.get(WAIT_SECONDS, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
				.hasCauseInstanceOf(VersionActionBlockedException.class)
				.cause()
				.satisfies(refused -> assertThat(((VersionActionBlockedException) refused).getDetails())
					.containsEntry("reason", BlockReason.RELATED_NOT_ONLINE));
		}

		Boolean arcOnline = transaction.execute(status -> this.arcRepository.findOnline(arc).isPresent());
		Boolean chapterOnline = transaction.execute(status -> this.chapterRepository.findOnline(chapter).isPresent());
		assertThat(arcOnline).isFalse();
		assertThat(chapterOnline).isFalse();
	}

	@Test
	void anArcRetiredWhileOneOfItsChaptersIsGoingOnlineWaitsAndIsRefusedOnceTheChapterIsOnline() throws Exception {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID saga = transaction.execute(status -> sagaContent());
		UUID arc = transaction.execute(status -> arcContent(saga, PUBLISHED));
		UUID chapter = transaction.execute(status -> chapterContent(arc, READY_TO_PUBLISH));
		var publishFoundArcOnline = new CountDownLatch(1);
		var letPublishGo = new CountDownLatch(1);
		ArcVersionRepository holdingRead = mock(ArcVersionRepository.class, delegatesTo(this.arcRepository));
		doAnswer(invocation -> {
			boolean online = this.arcRepository.existsWithStatus(arc, Set.of(PUBLISHED));
			publishFoundArcOnline.countDown();
			letPublishGo.await(WAIT_SECONDS, TimeUnit.SECONDS);
			return online;
		}).when(holdingRead).existsWithStatus(eq(arc), any());
		var chapters = chapterService(holdingRead);
		var arcs = arcService(this.chapterRepository);

		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<?> publish = executor
				.submit(() -> transaction.execute(status -> chapters.publish(PUBLISHER, this.luffy, chapter, 1)));
			assertThat(publishFoundArcOnline.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			Future<?> retire = executor
				.submit(() -> transaction.execute(status -> arcs.retire(RETIRER, this.vivi, arc, 1)));

			Thread.sleep(SETTLE_MILLIS);
			assertThat(retire.isDone()).as("the retirement waits for the publication").isFalse();
			letPublishGo.countDown();

			assertThat(publish.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
			assertThatThrownBy(() -> retire.get(WAIT_SECONDS, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
				.hasCauseInstanceOf(VersionActionBlockedException.class)
				.cause()
				.satisfies(refused -> assertThat(((VersionActionBlockedException) refused).getDetails())
					.containsEntry("reason", BlockReason.ONLINE_ITEMS_LINKED));
		}

		Boolean arcOnline = transaction.execute(status -> this.arcRepository.findOnline(arc).isPresent());
		Boolean chapterOnline = transaction.execute(status -> this.chapterRepository.findOnline(chapter).isPresent());
		assertThat(arcOnline).isTrue();
		assertThat(chapterOnline).isTrue();
	}

	private ChapterService chapterService(ArcVersionRepository arcs) {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		return new ChapterService(this.chapterRepository, this.contentVersionRepository, this.contentRepository,
				new ChapterValidator(this.chapterRepository, this.languageRepository, arcs, this.volumeRepository),
				new ChapterRules(this.contentRepository, arcs, this.volumeRepository),
				new AuditLogService(this.auditLogRepository, clock), clock);
	}

	private ArcService arcService(ChapterVersionRepository chapters) {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		return new ArcService(this.arcRepository, this.contentVersionRepository, this.contentRepository,
				new ArcValidator(this.arcRepository, this.languageRepository, this.sagaRepository),
				new ArcRules(this.contentRepository, this.sagaRepository, chapters, this.episodeRepository,
						new RulesProperties(5)),
				new AuditLogService(this.auditLogRepository, clock), clock);
	}

	private UUID sagaContent() {
		UUID contentId = content(EntityType.SAGA);
		int number = NUMBERS.incrementAndGet();
		var version = new SagaVersionEntity(workflow(contentId, PUBLISHED));
		StoryVersionMapper.rewrite(version,
				new Saga(number, Map.of("it", new StoryTranslation("Saga " + number, "Descrizione"))), NOW);
		this.sagaRepository.save(version);
		return contentId;
	}

	private UUID arcContent(UUID saga, VersionStatus status) {
		UUID contentId = content(EntityType.ARC);
		int number = NUMBERS.incrementAndGet();
		var version = new ArcVersionEntity(workflow(contentId, status));
		StoryVersionMapper.rewrite(version,
				new Arc(number, saga, Map.of("it", new StoryTranslation("Arc " + number, "Descrizione"))), NOW);
		this.arcRepository.save(version);
		return contentId;
	}

	private UUID chapterContent(UUID arc, VersionStatus status) {
		UUID contentId = content(EntityType.CHAPTER);
		var version = new ChapterVersionEntity(workflow(contentId, status));
		StoryVersionMapper.rewrite(version,
				Chapter.builder()
					.number(NUMBERS.incrementAndGet())
					.arcContentId(arc)
					.translations(Map.of("it", new StoryTranslation("Capitolo", "Descrizione")))
					.build(),
				NOW);
		this.chapterRepository.save(version);
		return contentId;
	}

	private UUID content(EntityType entityType) {
		return this.contentRepository.save(new ContentEntity(UUID.randomUUID(), entityType, NOW)).getId();
	}

	private ContentVersionEntity workflow(UUID contentId, VersionStatus status) {
		return ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(1)
			.author(UserMapper.toEmbeddable(this.vivi))
			.status(status)
			.createdAt(NOW)
			.updatedAt(NOW)
			.build();
	}

}
