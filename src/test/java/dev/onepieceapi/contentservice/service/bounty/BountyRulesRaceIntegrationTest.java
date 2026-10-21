package dev.onepieceapi.contentservice.service.bounty;

import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.PUBLISHER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Two bounties replacing the same one, published at the same instant, each in its own
 * transaction against a real PostgreSQL (Testcontainers) - where, without the lock every
 * change to the online chain takes, both would read "nobody replaces it yet" and both
 * would go online (implementation plan of the Bounty, B2). The side that comes first is
 * held right after it read what its rule needs, and the other is started: it must wait
 * for the first to finish, then find what the first wrote and be refused. Not run inside
 * a test transaction: each caller commits, as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BountyRulesRaceIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final long WAIT_SECONDS = 10;

	/** Long enough for a caller that is not waiting on a lock to have finished. */
	private static final long SETTLE_MILLIS = 700;

	private final User vivi = new User(UUID.randomUUID(), "vivi", "vivi@onepiece.local");

	private final User luffy = new User(UUID.randomUUID(), "luffy", "luffy@onepiece.local");

	@Autowired
	private BountyVersionRepository bountyRepository;

	@Autowired
	private CharacterVersionRepository characterRepository;

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

	private BountyFixtures fixtures;

	@BeforeEach
	void setUp() {
		this.fixtures = BountyFixtures.builder()
			.contents(this.contentRepository)
			.bounties(this.bountyRepository)
			.characters(this.characterRepository)
			.chapters(this.chapterRepository)
			.episodes(this.episodeRepository)
			.contentVersions(this.contentVersionRepository)
			.languages(this.languageRepository)
			.auditLog(this.auditLogRepository)
			.build();
	}

	@Test
	void twoBountiesReplacingTheSameOnePublishedTogetherGoOnlineOneAfterTheOtherAndOnlyTheFirstDoes() throws Exception {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID previous = transaction
			.execute(status -> this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Race One", 1L, null)));
		UUID first = transaction.execute(
				status -> this.fixtures.bounty(READY_TO_PUBLISH, this.fixtures.complete("Race One", 2L, previous)));
		UUID second = transaction.execute(
				status -> this.fixtures.bounty(READY_TO_PUBLISH, this.fixtures.complete("Race One", 3L, previous)));
		var firstChecked = new CountDownLatch(1);
		var letFirstGo = new CountDownLatch(1);
		BountyVersionRepository holdingCheck = mock(BountyVersionRepository.class, delegatesTo(this.bountyRepository));
		doAnswer(invocation -> {
			var others = this.bountyRepository.findOnlineOthersReplacing(previous, first, invocation.getArgument(2));
			firstChecked.countDown();
			letFirstGo.await(WAIT_SECONDS, TimeUnit.SECONDS);
			return others;
		}).when(holdingCheck).findOnlineOthersReplacing(eq(previous), eq(first), any());
		var holding = this.fixtures.service(holdingCheck, null);
		var free = this.fixtures.service();

		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<?> publishFirst = executor
				.submit(() -> transaction.execute(status -> holding.publish(PUBLISHER, this.luffy, first, 1)));
			assertThat(firstChecked.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			Future<?> publishSecond = executor
				.submit(() -> transaction.execute(status -> free.publish(PUBLISHER, this.vivi, second, 1)));

			Thread.sleep(SETTLE_MILLIS);
			assertThat(publishSecond.isDone()).as("the second publication waits for the first").isFalse();
			letFirstGo.countDown();

			assertThat(publishFirst.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
			assertThatThrownBy(() -> publishSecond.get(WAIT_SECONDS, TimeUnit.SECONDS))
				.isInstanceOf(ExecutionException.class)
				.hasCauseInstanceOf(VersionActionBlockedException.class)
				.cause()
				.satisfies(refused -> assertThat(((VersionActionBlockedException) refused).getDetails())
					.containsEntry("reason", BlockReason.PREVIOUS_ALREADY_REPLACED));
		}

		Boolean firstOnline = transaction.execute(status -> this.bountyRepository.findOnline(first).isPresent());
		Boolean secondOnline = transaction.execute(status -> this.bountyRepository.findOnline(second).isPresent());
		assertThat(firstOnline).isTrue();
		assertThat(secondOnline).isFalse();
	}

	@Test
	void aBountyRetiredWhileItsSuccessorIsGoingOnlineWaitsAndIsRefusedOnceTheSuccessorIsOnline() throws Exception {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID previous = transaction
			.execute(status -> this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Race Two", 1L, null)));
		UUID successor = transaction.execute(
				status -> this.fixtures.bounty(READY_TO_PUBLISH, this.fixtures.complete("Race Two", 2L, previous)));
		var successorChecked = new CountDownLatch(1);
		var letSuccessorGo = new CountDownLatch(1);
		BountyVersionRepository holdingCheck = mock(BountyVersionRepository.class, delegatesTo(this.bountyRepository));
		doAnswer(invocation -> {
			var others = this.bountyRepository.findOnlineOthersReplacing(previous, successor,
					invocation.getArgument(2));
			successorChecked.countDown();
			letSuccessorGo.await(WAIT_SECONDS, TimeUnit.SECONDS);
			return others;
		}).when(holdingCheck).findOnlineOthersReplacing(eq(previous), eq(successor), any());
		var publishing = this.fixtures.service(holdingCheck, null);
		var retiring = this.fixtures.service();

		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<?> publish = executor
				.submit(() -> transaction.execute(status -> publishing.publish(PUBLISHER, this.luffy, successor, 1)));
			assertThat(successorChecked.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			Future<?> retire = executor.submit(() -> transaction
				.execute(status -> retiring.retire(BountyFixtures.RETIRER, this.vivi, previous, 1)));

			Thread.sleep(SETTLE_MILLIS);
			assertThat(retire.isDone()).as("the retirement waits for the publication").isFalse();
			letSuccessorGo.countDown();

			assertThat(publish.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
			assertThatThrownBy(() -> retire.get(WAIT_SECONDS, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
				.hasCauseInstanceOf(VersionActionBlockedException.class)
				.cause()
				.satisfies(refused -> assertThat(((VersionActionBlockedException) refused).getDetails())
					.containsEntry("reason", BlockReason.ONLINE_ITEMS_LINKED));
		}

		Boolean previousOnline = transaction.execute(status -> this.bountyRepository.findOnline(previous).isPresent());
		Boolean successorOnline = transaction
			.execute(status -> this.bountyRepository.findOnline(successor).isPresent());
		assertThat(previousOnline).isTrue();
		assertThat(successorOnline).isTrue();
	}

}
