package dev.onepieceapi.contentservice.service.bounty;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import dev.onepieceapi.contentservice.domain.bounty.BountyTranslation;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.exception.SlugAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
import org.hibernate.exception.ConstraintViolationException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.EDITOR;
import static dev.onepieceapi.contentservice.service.bounty.BountyFixtures.fieldsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Creating a bounty and editing its draft against a real PostgreSQL (Testcontainers) -
 * the same workflow as every entity, covered with the Devil Fruit Type - and what is its
 * own: the composite key and its slug (implementation plan of the Bounty, D2), the link
 * to the bounty it replaces and the three rules about it on save (B2), and what the
 * schema guarantees whatever the service does.
 * <p>
 * Seeded for every test: Luffy 1,500,000,000 online, complete in both languages; Luffy
 * 3,000,000,000, a draft naming the first as its previous.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class BountyDraftIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

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
	private JdbcTemplate jdbc;

	@Autowired
	private TestEntityManager entityManager;

	private BountyFixtures fixtures;

	private BountyService service;

	private UUID luffyFirst;

	private UUID luffySecond;

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
		this.luffyFirst = this.fixtures.bounty(PUBLISHED,
				this.fixtures.complete("Monkey D. Luffy", 1_500_000_000L, null));
		this.luffySecond = this.fixtures.bounty(DRAFT,
				this.fixtures.complete("Monkey D. Luffy", 3_000_000_000L, this.luffyFirst));
		stored();
	}

	@Test
	void aNewBountyIsBornWithADraftSayingWhatWasWrittenTheSpaceAroundItIgnored() {
		UUID earlier = this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Roronoa Zoro", 120_000_000L, null));
		stored();
		var written = new Bounty("  Roronoa Zoro ", 320_000_000L, BountyCondition.ONLY_ALIVE, true, earlier, null,
				Map.of("it", new BountyTranslation(" Pirata  ")), null, StoryPoint.NONE);

		var content = this.service.create(EDITOR, CHOPPER, written);
		stored();

		var version = this.service.getVersion(EDITOR, CHOPPER, content.id(), 1).version();
		assertThat(version.status()).isEqualTo(DRAFT);
		assertThat(version.body().posterName()).isEqualTo("Roronoa Zoro");
		assertThat(version.body().amount()).isEqualTo(320_000_000L);
		assertThat(version.body().inactive()).isTrue();
		assertThat(version.body().previousContentId()).isEqualTo(earlier);
		assertThat(version.body().translations()).containsOnlyKeys("it");
	}

	@Test
	void theDatabaseWritesTheKeyAndItsSlugFromThePostersNameAndTheAmount() {
		var key = this.jdbc.queryForMap("select natural_key, key_slug from bounty_version where natural_key like ?",
				"Monkey D. Luffy 1500000000");

		assertThat(key).containsEntry("natural_key", "Monkey D. Luffy 1500000000")
			.containsEntry("key_slug", "monkey-d-luffy-1500000000");
	}

	@Test
	void aDraftWithoutAnAmountHasNoKeyYet() {
		var content = this.service.create(EDITOR, CHOPPER,
				new Bounty("Zoro", null, null, false, null, null, Map.of(), null, StoryPoint.NONE));
		stored();

		assertThat(this.jdbc.queryForObject("select natural_key from bounty_version where poster_name = 'Zoro'",
				String.class))
			.isNull();
		assertThat(content.versions()).hasSize(1);
	}

	@Test
	void theSamePosterNameAndAmountAnotherBountyHasIsRefusedNamingBothFieldsWhateverTheCase() {
		var written = this.fixtures.complete(" monkey d. LUFFY ", 1_500_000_000L, null);

		var refused = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactly("posterName", "amount");
	}

	@Test
	void theSamePosterNameWithAnotherAmountIsAnotherBounty() {
		var content = this.service.create(EDITOR, CHOPPER,
				this.fixtures.complete("Monkey D. Luffy", 1_500_000_001L, null));

		assertThat(content.versions()).hasSize(1);
	}

	@Test
	void aKeyGivingTheSlugOfAnotherBountyIsRefusedNamingBothFields() {
		// "Monkey D Luffy" is no equal text, but gives the same address:
		// monkey-d-luffy-1500000000.
		var written = this.fixtures.complete("Monkey D Luffy", 1_500_000_000L, null);

		var refused = catchThrowableOfType(SlugAlreadyUsedException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactly("posterName", "amount");
	}

	@Test
	void aBountyEditedKeepingItsOwnKeyDoesNotCollideWithItself() {
		this.service.edit(EDITOR, CHOPPER, this.luffySecond, 1,
				this.fixtures.complete("Monkey D. Luffy", 3_000_000_000L, this.luffyFirst));
		stored();

		assertThat(this.service.getVersion(EDITOR, CHOPPER, this.luffySecond, 1).version().body().amount())
			.isEqualTo(3_000_000_000L);
	}

	@ParameterizedTest
	@EnumSource(names = { "READY_TO_PUBLISH", "PUBLISHED", "ARCHIVED", "RETIRED", "SUPERSEDED" })
	void aBountyWithAnApprovedVersionCanBeNamedAsThePreviousWhateverBecameOfIt(VersionStatus status) {
		UUID approved = this.fixtures.bounty(status, this.fixtures.complete("Nami", 16_000_000L, null));
		stored();

		var content = this.service.create(EDITOR, CHOPPER, this.fixtures.complete("Nami", 66_000_000L, approved));

		assertThat(content.versions()).hasSize(1);
	}

	@ParameterizedTest
	@EnumSource(names = { "DRAFT", "IN_REVIEW", "REJECTED" })
	void aBountyWithNothingApprovedCannotBeNamedAsThePrevious(VersionStatus status) {
		UUID unapproved = this.fixtures.bounty(status, this.fixtures.complete("Nami", 16_000_000L, null));
		stored();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, this.fixtures.complete("Nami", 66_000_000L, unapproved)));

		assertThat(fieldsOf(refused)).containsExactly("previous");
	}

	@Test
	void anIdThatIsNoBountyIsRefusedLikeABountyNotToBeLinked() {
		UUID notABounty = this.contentRepository
			.save(new ContentEntity(UUID.randomUUID(), EntityType.CHAPTER, BountyFixtures.EARLIER))
			.getId();
		for (UUID id : new UUID[] { UUID.randomUUID(), notABounty }) {
			var refused = catchThrowableOfType(ValueInvalidException.class,
					() -> this.service.create(EDITOR, CHOPPER, this.fixtures.complete("Nami", 66_000_000L, id)));

			assertThat(fieldsOf(refused)).containsExactly("previous");
		}
	}

	@Test
	void aBountyCannotBeItsOwnPrevious() {
		var refused = catchThrowableOfType(ValueInvalidException.class, () -> this.service.edit(EDITOR, CHOPPER,
				this.luffySecond, 1, this.fixtures.complete("Monkey D. Luffy", 3_000_000_000L, this.luffySecond)));

		// Nothing approved of itself yet, so it is not linkable either: the first refusal
		// told.
		assertThat(fieldsOf(refused)).containsExactly("previous");
	}

	@Test
	void anApprovedBountyCannotBeItsOwnPreviousEither() {
		this.service.openNewVersion(EDITOR, CHOPPER, this.luffyFirst, 1);
		stored();

		var refused = catchThrowableOfType(ValueInvalidException.class, () -> this.service.edit(EDITOR, CHOPPER,
				this.luffyFirst, 2, this.fixtures.complete("Monkey D. Luffy", 1_500_000_000L, this.luffyFirst)));

		assertThat(fieldsOf(refused)).containsExactly("previous");
		assertThat(refused.getDetails().get("errors").toString()).contains("must be another bounty");
	}

	@Test
	void aBountyAnyVersionOfAnotherOneAlreadyReplacesCannotBeReplacedAgainEvenByADraft() {
		// luffySecond is a draft naming luffyFirst: that is enough.
		var refused = catchThrowableOfType(ValueAlreadyUsedException.class, () -> this.service.create(EDITOR, CHOPPER,
				this.fixtures.complete("Monkey D. Luffy", 3_500_000_000L, this.luffyFirst)));

		assertThat(fieldsOf(refused)).containsExactly("previous");
		assertThat(refused.getDetails().get("errors").toString()).contains("already replaced by another bounty");
	}

	@Test
	void aBountyMayKeepReplacingTheSameOneInANewVersionOfItself() {
		this.service.edit(EDITOR, CHOPPER, this.luffySecond, 1,
				this.fixtures.complete("Monkey D. Luffy", 3_100_000_000L, this.luffyFirst));
		stored();

		assertThat(this.service.getVersion(EDITOR, CHOPPER, this.luffySecond, 1).version().body().amount())
			.isEqualTo(3_100_000_000L);
	}

	@Test
	void aChainThatWouldCloseOnItselfIsRefusedOnSaveThroughTheApprovedVersions() {
		// B replaces A (both approved); a new draft of A naming B would make A -> B -> A.
		UUID a = this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Nami", 16_000_000L, null));
		UUID b = this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Nami", 66_000_000L, a));
		stored();
		this.service.openNewVersion(EDITOR, CHOPPER, a, 1);
		stored();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.edit(EDITOR, CHOPPER, a, 2, this.fixtures.complete("Nami", 16_000_000L, b)));

		assertThat(fieldsOf(refused)).containsExactly("previous");
		assertThat(refused.getDetails().get("errors").toString()).contains("would make the chain a cycle");
	}

	@Test
	void aLongerCycleIsRefusedToo() {
		UUID a = this.fixtures.bounty(PUBLISHED, this.fixtures.complete("Nami", 1L, null));
		UUID b = this.fixtures.bounty(READY_TO_PUBLISH, this.fixtures.complete("Nami", 2L, a));
		UUID c = this.fixtures.bounty(READY_TO_PUBLISH, this.fixtures.complete("Nami", 3L, b));
		stored();
		this.service.openNewVersion(EDITOR, CHOPPER, a, 1);
		stored();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.edit(EDITOR, CHOPPER, a, 2, this.fixtures.complete("Nami", 1L, c)));

		assertThat(fieldsOf(refused)).containsExactly("previous");
	}

	@Test
	void aChapterNothingApprovedOfCannotBeTheFirstAppearanceAndEveryLinkAtFaultIsNamed() {
		UUID draftChapter = OnlineChapters.chapter(this.contentRepository, this.chapterRepository, DRAFT);
		UUID draftPrevious = this.fixtures.bounty(DRAFT, this.fixtures.complete("Nami", 16_000_000L, null));
		stored();
		var written = new Bounty("Nami", 66_000_000L, BountyCondition.DEAD_OR_ALIVE, false, draftPrevious, null,
				Map.of(), null, StoryPoint.ofChapter(draftChapter));

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactly("previous", "firstAppearance.chapter");
	}

	@Test
	void aDraftMayBeSavedIncompleteButNotSentToReview() {
		var content = this.service.create(EDITOR, CHOPPER,
				new Bounty("Nami", 66_000_000L, null, false, null, null, Map.of(), null, StoryPoint.NONE));
		stored();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.service.submit(EDITOR, CHOPPER, content.id(), 1));

		assertThat(fieldsOf(refused)).containsExactly("condition", "firstAppearance", "translations[en].description",
				"translations[it].description");
	}

	@Test
	void aCompleteDraftIsSentToReview() {
		var content = this.service.create(EDITOR, CHOPPER, this.fixtures.complete("Nami", 66_000_000L, null));
		stored();

		assertThat(this.service.submit(EDITOR, CHOPPER, content.id(), 1).version().status())
			.isEqualTo(VersionStatus.IN_REVIEW);
	}

	@Test
	void aLanguageABountySaysSomethingInIsInUse() {
		assertThat(this.bountyRepository.existsByLanguage("it")).isTrue();
		assertThat(this.bountyRepository.existsByLanguage("fr")).isFalse();
	}

	@Test
	void theDatabaseRefusesABountyPointingToAnythingButABounty() {
		UUID chapter = OnlineChapters.chapter(this.contentRepository, this.chapterRepository, PUBLISHED);
		this.fixtures.bounty(DRAFT, this.fixtures.complete("Nami", 1L, chapter));

		assertThatThrownBy(this::stored).isInstanceOf(ConstraintViolationException.class)
			.hasMessageContaining("fk_bounty_version_previous");
	}

	@Test
	void theDatabaseKeepsABountyReplacedFromBeingDeleted() {
		assertThatThrownBy(() -> this.jdbc.update("delete from content where id = ?", this.luffyFirst))
			.hasMessageContaining("fk_bounty_version_previous");
	}

	@Test
	void theDatabaseRefusesANegativeAmount() {
		assertThatThrownBy(() -> this.jdbc.update("update bounty_version set amount = -1"))
			.hasMessageContaining("ck_bounty_version_amount");
	}

	@Test
	void theDatabaseRefusesAConditionThatIsNone() {
		assertThatThrownBy(() -> this.jdbc.update("update bounty_version set condition = 'ALIVE'"))
			.hasMessageContaining("ck_bounty_version_condition");
	}

	@ParameterizedTest
	@EnumSource(names = { "READY_TO_PUBLISH", "PUBLISHED", "ARCHIVED", "RETIRED", "SUPERSEDED" })
	void aCharacterWithAnApprovedVersionCanBeTheHolderWhateverBecameOfIt(VersionStatus status) {
		UUID nami = this.fixtures.character(status, "Nami");
		stored();

		var content = this.service.create(EDITOR, CHOPPER,
				BountyFixtures.heldBy(this.fixtures.complete("Nami", 16_000_000L, null), nami));
		stored();

		assertThat(this.service.getVersion(EDITOR, CHOPPER, content.id(), 1).version().body().holderContentId())
			.isEqualTo(nami);
	}

	@ParameterizedTest
	@EnumSource(names = { "DRAFT", "IN_REVIEW", "REJECTED" })
	void aCharacterWithNothingApprovedCannotBeTheHolder(VersionStatus status) {
		UUID unapproved = this.fixtures.character(status, "Nami");
		stored();

		var refused = catchThrowableOfType(ValueInvalidException.class, () -> this.service.create(EDITOR, CHOPPER,
				BountyFixtures.heldBy(this.fixtures.complete("Nami", 16_000_000L, null), unapproved)));

		assertThat(fieldsOf(refused)).containsExactly("holder");
	}

	@Test
	void anIdThatIsNoCharacterIsRefusedLikeACharacterNotToBeLinked() {
		UUID notACharacter = this.luffyFirst;
		for (UUID id : new UUID[] { UUID.randomUUID(), notACharacter }) {
			var refused = catchThrowableOfType(ValueInvalidException.class, () -> this.service.create(EDITOR, CHOPPER,
					BountyFixtures.heldBy(this.fixtures.complete("Nami", 16_000_000L, null), id)));

			assertThat(fieldsOf(refused)).containsExactly("holder");
		}
	}

	@Test
	void everyLinkAtFaultIsNamedTheHolderAmongThem() {
		UUID draftPrevious = this.fixtures.bounty(DRAFT, this.fixtures.complete("Nami", 16_000_000L, null));
		UUID draftHolder = this.fixtures.character(DRAFT, "Nami");
		stored();

		var refused = catchThrowableOfType(ValueInvalidException.class, () -> this.service.create(EDITOR, CHOPPER,
				BountyFixtures.heldBy(this.fixtures.complete("Nami", 66_000_000L, draftPrevious), draftHolder)));

		assertThat(fieldsOf(refused)).containsExactly("previous", "holder");
	}

	@Test
	void theDatabaseRefusesABountyPointingToAnythingButACharacter() {
		UUID chapter = OnlineChapters.chapter(this.contentRepository, this.chapterRepository, PUBLISHED);
		this.fixtures.bounty(DRAFT, BountyFixtures.heldBy(this.fixtures.complete("Nami", 1L, null), chapter));

		assertThatThrownBy(this::stored).isInstanceOf(ConstraintViolationException.class)
			.hasMessageContaining("fk_bounty_version_holder");
	}

	@Test
	void theDatabaseKeepsACharacterHoldingABountyFromBeingDeleted() {
		UUID nami = this.fixtures.character(PUBLISHED, "Nami");
		this.fixtures.bounty(DRAFT, BountyFixtures.heldBy(this.fixtures.complete("Nami", 1L, null), nami));
		stored();

		assertThatThrownBy(() -> this.jdbc.update("delete from content where id = ?", nami))
			.hasMessageContaining("fk_bounty_version_holder");
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
