package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.FruitUse;
import dev.onepieceapi.contentservice.domain.character.Relative;
import dev.onepieceapi.contentservice.domain.character.RelativeKind;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.PUBLISHER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Two characters with the same current Devil Fruit, published at the same instant, each
 * in its own transaction against a real PostgreSQL (Testcontainers) - where, without the
 * lock a current fruit is held with, both would read "nobody else has it" and both would
 * go online (implementation plan of the Character, C10, D12). The side that comes first
 * is held right after it read the users of the fruit, and the other is started: it must
 * wait for the first to finish, then find what the first wrote and be refused. Not run
 * inside a test transaction: each caller commits, as in production. The same for two
 * characters going online each naming the other as a relative (C12, D13): the pair is
 * held by an advisory lock.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CharacterRulesRaceIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final long WAIT_SECONDS = 10;

	/** Long enough for a caller that is not waiting on a lock to have finished. */
	private static final long SETTLE_MILLIS = 700;

	private final User vivi = new User(UUID.randomUUID(), "vivi", "vivi@onepiece.local");

	private final User luffy = new User(UUID.randomUUID(), "luffy", "luffy@onepiece.local");

	@Autowired
	private CharacterVersionRepository characterRepository;

	@Autowired
	private DevilFruitVersionRepository devilFruitRepository;

	@Autowired
	private SpeciesVersionRepository speciesRepository;

	@Autowired
	private BountyVersionRepository bountyRepository;

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

	private CharacterFixtures fixtures;

	@BeforeEach
	void setUp() {
		var transaction = new TransactionTemplate(this.transactionManager);
		this.fixtures = CharacterFixtures.builder()
			.contents(this.contentRepository)
			.characters(this.characterRepository)
			.species(this.speciesRepository)
			.devilFruits(this.devilFruitRepository)
			.bounties(this.bountyRepository)
			.chapters(this.chapterRepository)
			.episodes(this.episodeRepository)
			.contentVersions(this.contentVersionRepository)
			.languages(this.languageRepository)
			.auditLog(this.auditLogRepository)
			.firstAppearance(transaction
				.execute(status -> OnlineChapters.onlineChapter(this.contentRepository, this.chapterRepository)))
			.build();
	}

	@Test
	void twoCharactersWithTheSameCurrentFruitPublishedTogetherGoOnlineOneAfterTheOtherAndOnlyTheFirstDoes()
			throws Exception {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID fruit = transaction.execute(status -> this.fixtures.devilFruit(PUBLISHED, "Race Mera", false));
		UUID first = transaction.execute(status -> this.fixtures.character(READY_TO_PUBLISH, withFruit("Ace", fruit)));
		UUID second = transaction
			.execute(status -> this.fixtures.character(READY_TO_PUBLISH, withFruit("Sabo", fruit)));
		var firstChecked = new CountDownLatch(1);
		var letFirstGo = new CountDownLatch(1);
		CharacterVersionRepository holdingCheck = mock(CharacterVersionRepository.class,
				delegatesTo(this.characterRepository));
		doAnswer(invocation -> {
			var others = this.characterRepository.findOnlineOtherCurrentUsersOf(fruit, first,
					invocation.getArgument(2));
			firstChecked.countDown();
			letFirstGo.await(WAIT_SECONDS, TimeUnit.SECONDS);
			return others;
		}).when(holdingCheck).findOnlineOtherCurrentUsersOf(eq(fruit), eq(first), any());
		var holding = this.fixtures.service(holdingCheck);
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
					.containsEntry("reason", BlockReason.DEVIL_FRUIT_IN_USE));
		}

		Boolean firstOnline = transaction.execute(status -> this.characterRepository.findOnline(first).isPresent());
		Boolean secondOnline = transaction.execute(status -> this.characterRepository.findOnline(second).isPresent());
		assertThat(firstOnline).isTrue();
		assertThat(secondOnline).isFalse();
	}

	@Test
	void twoCharactersNamingEachOtherPublishedTogetherGoOnlineOneAfterTheOtherAndOnlyTheFirstDoes() throws Exception {
		var transaction = new TransactionTemplate(this.transactionManager);
		UUID first = transaction
			.execute(status -> this.fixtures.character(PUBLISHED, this.fixtures.complete("Pair Ace")));
		UUID second = transaction
			.execute(status -> this.fixtures.character(PUBLISHED, this.fixtures.complete("Pair Sabo")));
		transaction.executeWithoutResult(status -> {
			this.fixtures.characterVersion(first, 2, READY_TO_PUBLISH, withRelative("Pair Ace", second));
			this.fixtures.characterVersion(second, 2, READY_TO_PUBLISH, withRelative("Pair Sabo", first));
		});
		var firstChecked = new CountDownLatch(1);
		var letFirstGo = new CountDownLatch(1);
		CharacterVersionRepository holdingCheck = mock(CharacterVersionRepository.class,
				delegatesTo(this.characterRepository));
		doAnswer(invocation -> {
			boolean linked = this.characterRepository.namesAsRelative(second, first, invocation.getArgument(2));
			firstChecked.countDown();
			letFirstGo.await(WAIT_SECONDS, TimeUnit.SECONDS);
			return linked;
		}).when(holdingCheck).namesAsRelative(eq(second), eq(first), any());
		var holding = this.fixtures.service(holdingCheck);
		var free = this.fixtures.service();

		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<?> publishFirst = executor
				.submit(() -> transaction.execute(status -> holding.publish(PUBLISHER, this.luffy, first, 2)));
			assertThat(firstChecked.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			Future<?> publishSecond = executor
				.submit(() -> transaction.execute(status -> free.publish(PUBLISHER, this.vivi, second, 2)));

			Thread.sleep(SETTLE_MILLIS);
			assertThat(publishSecond.isDone()).as("the second publication waits for the first").isFalse();
			letFirstGo.countDown();

			assertThat(publishFirst.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();
			assertThatThrownBy(() -> publishSecond.get(WAIT_SECONDS, TimeUnit.SECONDS))
				.isInstanceOf(ExecutionException.class)
				.hasCauseInstanceOf(VersionActionBlockedException.class)
				.cause()
				.satisfies(refused -> assertThat(((VersionActionBlockedException) refused).getDetails())
					.containsEntry("reason", BlockReason.RELATIVE_ALREADY_LINKED));
		}

		Boolean firstNamesSecond = transaction
			.execute(status -> this.characterRepository.namesAsRelative(first, second, Set.of(PUBLISHED)));
		Boolean secondNamesFirst = transaction
			.execute(status -> this.characterRepository.namesAsRelative(second, first, Set.of(PUBLISHED)));
		assertThat(firstNamesSecond).isTrue();
		assertThat(secondNamesFirst).isFalse();
	}

	private Character withRelative(String romaji, UUID relative) {
		return this.fixtures.complete(romaji)
			.toBuilder()
			.relatives(List.of(new Relative(relative, RelativeKind.SIBLING)))
			.build();
	}

	private Character withFruit(String romaji, UUID fruit) {
		return this.fixtures.complete(romaji).toBuilder().devilFruits(List.of(new FruitUse(fruit, false))).build();
	}

}
