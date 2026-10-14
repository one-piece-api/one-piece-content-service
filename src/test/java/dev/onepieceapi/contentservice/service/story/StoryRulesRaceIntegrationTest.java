package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
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
 * An arc going online and its saga being retired at the same instant, each in its own
 * transaction against a real PostgreSQL (Testcontainers) - where, without the lock on the
 * saga's content, both would read a state the other is about to change and both would
 * pass, leaving an arc online in a saga that is not (implementation plan of the story
 * structure, S5). The side that comes first is held right after it read what its rule
 * needs, and the other is started: it must wait for the first to finish, then find what
 * the first wrote and be refused. Not run inside a test transaction: each caller commits,
 * as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StoryRulesRaceIntegrationTest {

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
	private static final AtomicInteger NUMBERS = new AtomicInteger(5000);

	private final User vivi = new User(UUID.randomUUID(), "vivi", "vivi@onepiece.local");

	private final User luffy = new User(UUID.randomUUID(), "luffy", "luffy@onepiece.local");

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
	private PlatformTransactionManager transactionManager;

	@Test
	void anArcPublishedWhileItsSagaIsBeingRetiredWaitsAndIsRefusedOnceTheSagaIsOffline() throws Exception {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID saga = transaction.execute(status -> sagaContent(PUBLISHED));
		UUID arc = transaction.execute(status -> arcContent(saga, READY_TO_PUBLISH));
		var retireCountedArcs = new CountDownLatch(1);
		var letRetireGo = new CountDownLatch(1);
		ArcVersionRepository holdingCount = mock(ArcVersionRepository.class, delegatesTo(this.arcRepository));
		doAnswer(invocation -> {
			long count = this.arcRepository.countOnlineLinkedTo(saga);
			retireCountedArcs.countDown();
			letRetireGo.await(WAIT_SECONDS, TimeUnit.SECONDS);
			return count;
		}).when(holdingCount).countOnlineLinkedTo(saga);
		var sagas = sagaService(holdingCount);
		var arcs = arcService(this.sagaRepository);

		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<?> retire = executor
				.submit(() -> transaction.execute(status -> sagas.retire(RETIRER, this.vivi, saga, 1)));
			assertThat(retireCountedArcs.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			Future<?> publish = executor
				.submit(() -> transaction.execute(status -> arcs.publish(PUBLISHER, this.luffy, arc, 1)));

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

		Boolean sagaOnline = transaction.execute(status -> this.sagaRepository.findOnline(saga).isPresent());
		Boolean arcOnline = transaction.execute(status -> this.arcRepository.findOnline(arc).isPresent());
		assertThat(sagaOnline).isFalse();
		assertThat(arcOnline).isFalse();
	}

	@Test
	void aSagaRetiredWhileOneOfItsArcsIsGoingOnlineWaitsAndIsRefusedOnceTheArcIsOnline() throws Exception {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID saga = transaction.execute(status -> sagaContent(PUBLISHED));
		UUID arc = transaction.execute(status -> arcContent(saga, READY_TO_PUBLISH));
		var publishFoundSagaOnline = new CountDownLatch(1);
		var letPublishGo = new CountDownLatch(1);
		SagaVersionRepository holdingRead = mock(SagaVersionRepository.class, delegatesTo(this.sagaRepository));
		doAnswer(invocation -> {
			boolean online = this.sagaRepository.existsWithStatus(saga, Set.of(PUBLISHED));
			publishFoundSagaOnline.countDown();
			letPublishGo.await(WAIT_SECONDS, TimeUnit.SECONDS);
			return online;
		}).when(holdingRead).existsWithStatus(eq(saga), any());
		var arcs = arcService(holdingRead);
		var sagas = sagaService(this.arcRepository);

		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<?> publish = executor
				.submit(() -> transaction.execute(status -> arcs.publish(PUBLISHER, this.luffy, arc, 1)));
			assertThat(publishFoundSagaOnline.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			Future<?> retire = executor
				.submit(() -> transaction.execute(status -> sagas.retire(RETIRER, this.vivi, saga, 1)));

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

		Boolean sagaOnline = transaction.execute(status -> this.sagaRepository.findOnline(saga).isPresent());
		Boolean arcOnline = transaction.execute(status -> this.arcRepository.findOnline(arc).isPresent());
		assertThat(sagaOnline).isTrue();
		assertThat(arcOnline).isTrue();
	}

	private SagaService sagaService(ArcVersionRepository arcs) {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		return new SagaService(this.sagaRepository, this.contentVersionRepository, this.contentRepository,
				new SagaValidator(this.sagaRepository, this.languageRepository),
				new SagaRules(this.contentRepository, arcs, new RulesProperties(5)),
				new AuditLogService(this.auditLogRepository, clock), clock);
	}

	private ArcService arcService(SagaVersionRepository sagas) {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		return new ArcService(this.arcRepository, this.contentVersionRepository, this.contentRepository,
				new ArcValidator(this.arcRepository, this.languageRepository, sagas),
				new ArcRules(this.contentRepository, sagas, this.chapterRepository, this.episodeRepository,
						new RulesProperties(5)),
				new AuditLogService(this.auditLogRepository, clock), clock);
	}

	private UUID sagaContent(VersionStatus status) {
		UUID contentId = this.contentRepository.save(new ContentEntity(UUID.randomUUID(), EntityType.SAGA, NOW))
			.getId();
		int number = NUMBERS.incrementAndGet();
		var version = new SagaVersionEntity(workflow(contentId, status));
		StoryVersionMapper.rewrite(version,
				new Saga(number, Map.of("it", new StoryTranslation("Saga " + number, "Descrizione"))), NOW);
		this.sagaRepository.save(version);
		return contentId;
	}

	private UUID arcContent(UUID saga, VersionStatus status) {
		UUID contentId = this.contentRepository.save(new ContentEntity(UUID.randomUUID(), EntityType.ARC, NOW)).getId();
		int number = NUMBERS.incrementAndGet();
		var version = new ArcVersionEntity(workflow(contentId, status));
		StoryVersionMapper.rewrite(version,
				new Arc(number, saga, Map.of("it", new StoryTranslation("Arc " + number, "Descrizione"))), NOW);
		this.arcRepository.save(version);
		return contentId;
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
