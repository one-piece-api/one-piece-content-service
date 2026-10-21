package dev.onepieceapi.contentservice.service.bounty;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.BlockedAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.RETIRED;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.ADMIN;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.EDITOR;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.PUBLISHER;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.REVIEWER;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.RETIRER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * What a bounty needs to go online and to be retired (implementation plan of the Bounty,
 * B2), against a real PostgreSQL (Testcontainers): the bounty it replaces and its first
 * appearance online, at most one online bounty replacing a bounty, no cycle through the
 * online ones, and no retirement while a successor is online. Whoever tries anyway is
 * refused with the reason, even an administrator.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class BountyRulesIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final User ZORO = new User(UUID.randomUUID(), "zoro", "zoro@onepiece.local");

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
	private TestEntityManager entityManager;

	private BountyFixtures fixtures;

	private BountyService service;

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
		this.service = this.fixtures.service();
	}

	@Test
	void aFirstBountyGoesOnlineWithNothingToWaitFor() {
		UUID first = this.fixtures.bounty(READY_TO_PUBLISH,
				this.fixtures.complete("Monkey D. Luffy", 1_500_000_000L, null));
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, first, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void aBountyGoesOnlineWhileThePreviousOneIsAndTheSlugItsKeyGivesIsKept() {
		UUID first = this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Monkey D. Luffy", 1_500_000_000L, null));
		UUID second = this.fixtures.bounty(READY_TO_PUBLISH,
				this.fixtures.complete("Monkey D. Luffy", 3_000_000_000L, first));
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, second, 1).version().status()).isEqualTo(PUBLISHED);
		stored();
		assertThat(this.bountyRepository.slugIsTakenByAnother("monkey-d-luffy-3000000000", UUID.randomUUID())).isTrue();
	}

	@ParameterizedTest
	@EnumSource(names = { "READY_TO_PUBLISH", "ARCHIVED", "RETIRED" })
	void aBountyDoesNotGoOnlineWhileThePreviousOneIsNotAndTheRefusalNamesIt(VersionStatus previousStatus) {
		UUID first = this.fixtures.bounty(previousStatus,
				this.fixtures.complete("Monkey D. Luffy", 1_500_000_000L, null));
		UUID second = this.fixtures.bounty(READY_TO_PUBLISH,
				this.fixtures.complete("Monkey D. Luffy", 3_000_000_000L, first));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(PUBLISHER, CHOPPER, second, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("previousId", first, "previousKey", "Monkey D. Luffy 1500000000"));
	}

	@Test
	void aBountyTakenOfflineIsRestoredWhileThePreviousOneIsOnlineAndNotBefore() {
		UUID offline = this.fixtures.bounty(RETIRED, this.fixtures.complete("Nami", 16_000_000L, null));
		UUID online = this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Zoro", 120_000_000L, null));
		UUID early = this.fixtures.bounty(RETIRED, this.fixtures.complete("Nami", 66_000_000L, offline));
		UUID fine = this.fixtures.bounty(RETIRED, this.fixtures.complete("Zoro", 320_000_000L, online));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.restore(PUBLISHER, CHOPPER, early, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(this.service.restore(PUBLISHER, CHOPPER, fine, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void aBountyWithoutAFirstAppearanceDoesNotGoOnlineEvenForAnAdministrator() {
		var withoutOne = new Bounty("Nami", 16_000_000L, BountyCondition.DEAD_OR_ALIVE, false, null, null, Map.of(),
				null, StoryPoint.NONE);
		UUID bounty = this.fixtures.bounty(READY_TO_PUBLISH, withoutOne);
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(ADMIN, CHOPPER, bounty, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.FIRST_APPEARANCE_MISSING);
	}

	@Test
	void aBountyDoesNotGoOnlineWhileItsChapterIsNot() {
		UUID chapter = OnlineChapters.chapter(this.contentRepository, this.chapterRepository, ARCHIVED);
		var body = this.fixtures.complete("Nami", 16_000_000L, null);
		UUID bounty = this.fixtures.bounty(READY_TO_PUBLISH, new Bounty(body.posterName(), body.amount(),
				body.condition(), false, null, null, body.translations(), null, StoryPoint.ofChapter(chapter)));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(PUBLISHER, CHOPPER, bounty, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("firstAppearanceChapterId", chapter, "firstAppearanceChapterNumber", 1));
	}

	@Test
	void aBountyDoesNotGoOnlineWhileAnotherOnlineBountyReplacesTheSamePreviousAndTheRefusalNamesIt() {
		UUID first = this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Monkey D. Luffy", 1_500_000_000L, null));
		UUID taken = this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Monkey D. Luffy", 3_000_000_000L, first));
		UUID late = this.fixtures.bounty(READY_TO_PUBLISH,
				this.fixtures.complete("Monkey D. Luffy", 3_100_000_000L, first));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(ADMIN, CHOPPER, late, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.PREVIOUS_ALREADY_REPLACED);
		assertThat(refused.getDetails().get("detail")).isEqualTo(
				Map.of("previousId", first, "replacedBy", Map.of("id", taken, "key", "Monkey D. Luffy 3000000000")));
		assertThat(this.service.getVersion(PUBLISHER, CHOPPER, late, 1).blockedActions())
			.extracting(BlockedAction::action, BlockedAction::reason)
			.containsExactly(tuple(VersionAction.PUBLISH, BlockReason.PREVIOUS_ALREADY_REPLACED));
	}

	@Test
	void aBountyMayReplaceThePreviousOneOnceTheOtherOnlineOneIsRetired() {
		UUID first = this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Monkey D. Luffy", 1_500_000_000L, null));
		UUID taken = this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Monkey D. Luffy", 3_000_000_000L, first));
		UUID late = this.fixtures.bounty(READY_TO_PUBLISH,
				this.fixtures.complete("Monkey D. Luffy", 3_100_000_000L, first));
		stored();
		this.service.retire(RETIRER, CHOPPER, taken, 1);
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, late, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void aRestoreIsRefusedWhileAnotherOnlineBountyReplacesTheSamePrevious() {
		UUID first = this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Monkey D. Luffy", 1_500_000_000L, null));
		UUID retired = this.fixtures.bounty(RETIRED, this.fixtures.complete("Monkey D. Luffy", 3_000_000_000L, first));
		this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Monkey D. Luffy", 3_100_000_000L, first));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.restore(ADMIN, CHOPPER, retired, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.PREVIOUS_ALREADY_REPLACED);
	}

	@Test
	void twoDraftsThatBothPassedTheSaveCheckCannotCloseACycleOnceOnline() {
		// A and B are online with no previous. A new draft of each names the other: each
		// is
		// checked against the approved versions, which are still the ones without a
		// previous.
		UUID a = this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Nami", 16_000_000L, null));
		UUID b = this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Nami", 66_000_000L, null));
		stored();
		this.service.openNewVersion(EDITOR, CHOPPER, a, 1);
		this.service.openNewVersion(EDITOR, CHOPPER, b, 1);
		stored();
		this.service.edit(EDITOR, CHOPPER, a, 2, this.fixtures.complete("Nami", 16_000_001L, b));
		this.service.edit(EDITOR, CHOPPER, b, 2, this.fixtures.complete("Nami", 66_000_001L, a));
		stored();
		// Submission checks again, still against the approved versions: both pass, as
		// neither is
		// approved yet.
		this.service.submit(EDITOR, CHOPPER, a, 2);
		this.service.submit(EDITOR, CHOPPER, b, 2);
		stored();
		approve(a, 2);
		approve(b, 2);

		assertThat(this.service.publish(PUBLISHER, CHOPPER, a, 2).version().status()).isEqualTo(PUBLISHED);
		stored();
		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(ADMIN, CHOPPER, b, 2));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.BOUNTY_CHAIN_CYCLE);
		assertThat(refused.getDetails().get("detail")).isEqualTo(Map.of("previousId", a));
	}

	@Test
	void aBountyIsNotRetiredWhileAnOnlineBountyReplacesItAndTheRefusalNamesIt() {
		UUID first = this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Monkey D. Luffy", 1_500_000_000L, null));
		UUID second = this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Monkey D. Luffy", 3_000_000_000L, first));
		this.fixtures.bounty(READY_TO_PUBLISH, this.fixtures.complete("Monkey D. Luffy", 3_100_000_000L, first));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.retire(ADMIN, CHOPPER, first, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.ONLINE_ITEMS_LINKED);
		assertThat(refused.getDetails().get("detail")).isEqualTo(
				Map.of("count", 1L, "bounties", List.of(Map.of("id", second, "key", "Monkey D. Luffy 3000000000"))));

		this.service.retire(RETIRER, CHOPPER, second, 1);
		stored();
		assertThat(this.service.retire(RETIRER, CHOPPER, first, 1).version().status()).isEqualTo(RETIRED);
	}

	@Test
	void aBountyGoesOnlineWhileItsHolderIs() {
		UUID nami = this.fixtures.character(PUBLISHED, "Nami");
		UUID bounty = this.fixtures.bounty(READY_TO_PUBLISH,
				BountyFixtures.heldBy(this.fixtures.complete("Nami", 16_000_000L, null), nami));
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, bounty, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@ParameterizedTest
	@EnumSource(names = { "READY_TO_PUBLISH", "ARCHIVED", "RETIRED" })
	void aBountyDoesNotGoOnlineWhileItsHolderIsNotAndTheRefusalNamesIt(VersionStatus holderStatus) {
		UUID nami = this.fixtures.character(holderStatus, "Nami");
		UUID bounty = this.fixtures.bounty(READY_TO_PUBLISH,
				BountyFixtures.heldBy(this.fixtures.complete("Nami", 16_000_000L, null), nami));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(PUBLISHER, CHOPPER, bounty, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail")).isEqualTo(Map.of("holderId", nami, "holderRomaji", "Nami"));
	}

	@Test
	void aBountyTakenOfflineIsRestoredWhileItsHolderIsOnlineAndNotBefore() {
		UUID offline = this.fixtures.character(RETIRED, "Nami");
		UUID online = this.fixtures.character(PUBLISHED, "Zoro");
		UUID early = this.fixtures.bounty(RETIRED,
				BountyFixtures.heldBy(this.fixtures.complete("Nami", 16_000_000L, null), offline));
		UUID fine = this.fixtures.bounty(RETIRED,
				BountyFixtures.heldBy(this.fixtures.complete("Zoro", 120_000_000L, null), online));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.restore(PUBLISHER, CHOPPER, early, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(this.service.restore(PUBLISHER, CHOPPER, fine, 1).version().status()).isEqualTo(PUBLISHED);
	}

	private void approve(UUID contentId, int number) {
		this.service.claim(REVIEWER, ZORO, contentId, number);
		this.service.approve(REVIEWER, ZORO, contentId, number);
		stored();
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
