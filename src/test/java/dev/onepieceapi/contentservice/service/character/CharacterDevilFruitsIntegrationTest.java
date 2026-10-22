package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.FruitUse;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.BlockedAction;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
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
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitLinks;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.ADMIN;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.EDITOR;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.PUBLISHER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.fieldsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The Devil Fruits of a character (implementation plan of the Character, C10, D12),
 * against a real PostgreSQL (Testcontainers): kept in their order with the former flag;
 * the save refusing what it should, entry by entry, and never the fruit another character
 * has; what a character needs to go online with them - one current user per fruit, unless
 * the fruit has several - and what a fruit owes its characters; the list narrowed by
 * fruit. Whoever breaks a rule is refused with the reason, even an administrator.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class CharacterDevilFruitsIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

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
	private JdbcTemplate jdbc;

	@Autowired
	private TestEntityManager entityManager;

	private CharacterFixtures fixtures;

	private CharacterService service;

	private UUID meraMera;

	private UUID smile;

	@BeforeEach
	void setUp() {
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
			.firstAppearance(OnlineChapters.onlineChapter(this.contentRepository, this.chapterRepository))
			.build();
		this.service = this.fixtures.service();
		this.meraMera = this.fixtures.devilFruit(PUBLISHED, "Mera Mera no Mi", false);
		this.smile = this.fixtures.devilFruit(PUBLISHED, "SMILE Zoan", true);
		stored();
	}

	// ---- Saving

	@Test
	void aNewCharacterKeepsItsFruitsInTheirOrderWithTheFormerFlag() {
		var written = withFruits("Sabo", use(this.meraMera, false), use(this.smile, true));

		var content = this.service.create(EDITOR, CHOPPER, written);
		stored();

		assertThat(this.service.getVersion(EDITOR, CHOPPER, content.id(), 1).version().body().devilFruits())
			.containsExactly(use(this.meraMera, false), use(this.smile, true));
	}

	@Test
	void reorderingAndShorteningTheFruitsOfADraftKeepsTheNewList() {
		var first = use(this.meraMera, true);
		var second = use(this.smile, false);
		var written = withFruits("Ace", first, second);
		var content = this.service.create(EDITOR, CHOPPER, written);
		stored();

		this.service.edit(EDITOR, CHOPPER, content.id(), 1,
				written.toBuilder().devilFruits(List.of(second, first)).build());
		stored();
		// What the commit would check: the fruits, swapped row by row, end up distinct.
		this.jdbc.execute("set constraints uq_character_version_devil_fruit immediate");
		assertThat(this.service.getVersion(EDITOR, CHOPPER, content.id(), 1).version().body().devilFruits())
			.containsExactly(second, first);

		this.jdbc.execute("set constraints uq_character_version_devil_fruit deferred");
		this.service.edit(EDITOR, CHOPPER, content.id(), 1, written.toBuilder().devilFruits(List.of(first)).build());
		stored();
		assertThat(this.service.getVersion(EDITOR, CHOPPER, content.id(), 1).version().body().devilFruits())
			.containsExactly(first);
	}

	@Test
	void aNewVersionStartsFromItsBaseWithEveryFruitInItsOrder() {
		var body = withFruits("Ace", use(this.smile, true), use(this.meraMera, false));
		UUID ace = this.fixtures.character(PUBLISHED, body);
		stored();

		this.service.openNewVersion(EDITOR, CHOPPER, ace, 1);
		stored();

		assertThat(this.service.getVersion(EDITOR, CHOPPER, ace, 2).version().body()).isEqualTo(body);
	}

	@Test
	void aFruitNamedTwiceIsRefusedByTheIndexOfItsRepeat() {
		var written = Character.builder()
			.romaji("Ace")
			.devilFruits(List.of(use(this.meraMera, false), use(this.smile, false), use(this.meraMera, true)))
			.build();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactly("devilFruits[2].devilFruit");
	}

	@Test
	void aFruitNothingApprovedOfIsRefusedByTheIndexOfItsEntry() {
		UUID draft = this.fixtures.devilFruit(DRAFT, "Hana Hana no Mi", false);
		stored();
		var written = Character.builder()
			.romaji("Robin")
			.devilFruits(List.of(use(this.meraMera, false), use(draft, false), use(UUID.randomUUID(), false)))
			.build();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactly("devilFruits[1].devilFruit", "devilFruits[2].devilFruit");
	}

	@Test
	void aFruitAnotherCharacterHasAsACurrentOneIsNeverRefusedOnSave() {
		this.fixtures.character(PUBLISHED, withFruits("Ace", use(this.meraMera, false)));
		stored();

		var content = this.service.create(EDITOR, CHOPPER, withFruits("Sabo", use(this.meraMera, false)));
		stored();

		assertThat(this.service.submit(EDITOR, CHOPPER, content.id(), 1).version().status()).isEqualTo(IN_REVIEW);
	}

	@Test
	void theDatabaseRefusesAFruitThatIsNoDevilFruit() {
		UUID ningen = this.fixtures.species(PUBLISHED, "Ningen");
		this.fixtures.character(DRAFT, withFruits("Ace", use(ningen, false)));

		assertThatThrownBy(this::stored).isInstanceOf(ConstraintViolationException.class)
			.hasMessageContaining("fk_character_version_devil_fruit");
	}

	@Test
	void theDatabaseRefusesTheSameFruitTwiceInAVersionOnceTheTransactionChecksIt() {
		this.fixtures.character(DRAFT, withFruits("Ace", use(this.meraMera, false), use(this.meraMera, true)));
		stored();

		assertThatThrownBy(() -> this.jdbc.execute("set constraints uq_character_version_devil_fruit immediate"))
			.hasMessageContaining("uq_character_version_devil_fruit");
	}

	// ---- Going online

	@Test
	void aCharacterGoesOnlineWithItsFruitsWhileEveryOneIsOnline() {
		UUID ace = this.fixtures.character(READY_TO_PUBLISH,
				withFruits("Ace", use(this.meraMera, false), use(this.smile, true)));
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, ace, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void aCharacterDoesNotGoOnlineWhileAFruitOfItsListIsNotNamingItsIndexEvenForAnAdministrator() {
		UUID hana = this.fixtures.devilFruit(ARCHIVED, "Hana Hana no Mi", false);
		UUID robin = this.fixtures.character(READY_TO_PUBLISH,
				withFruits("Robin", use(this.meraMera, true), use(hana, false)));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(ADMIN, CHOPPER, robin, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("devilFruitsIndex", 1, "devilFruitId", hana, "devilFruitRomaji", "Hana Hana no Mi"));
	}

	@Test
	void aSecondCurrentUserOfAFruitDoesNotGoOnlineNamingTheFirstEvenForAnAdministrator() {
		UUID ace = this.fixtures.character(PUBLISHED, withFruits("Ace", use(this.meraMera, false)));
		UUID sabo = this.fixtures.character(READY_TO_PUBLISH,
				withFruits("Sabo", use(this.smile, false), use(this.meraMera, false)));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(ADMIN, CHOPPER, sabo, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.DEVIL_FRUIT_IN_USE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("devilFruitsIndex", 1, "devilFruitId", this.meraMera, "devilFruitRomaji",
					"Mera Mera no Mi", "characters", List.of(Map.of("id", ace, "romaji", "Ace"))));
	}

	@Test
	void theBlockIsShownInAdvanceAsEveryOther() {
		this.fixtures.character(PUBLISHED, withFruits("Ace", use(this.meraMera, false)));
		UUID sabo = this.fixtures.character(READY_TO_PUBLISH, withFruits("Sabo", use(this.meraMera, false)));
		stored();

		var blocked = this.service.getVersion(ADMIN, CHOPPER, sabo, 1).blockedActions();

		assertThat(blocked).extracting(BlockedAction::action, BlockedAction::reason)
			.containsExactly(tuple(VersionAction.PUBLISH, BlockReason.DEVIL_FRUIT_IN_USE));
	}

	@Test
	void severalCurrentUsersOfAFruitWithSeveralUsersAllGoOnline() {
		this.fixtures.character(PUBLISHED, withFruits("Caesar", use(this.smile, false)));
		UUID momonosuke = this.fixtures.character(READY_TO_PUBLISH, withFruits("Momonosuke", use(this.smile, false)));
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, momonosuke, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void aFormerUserDoesNotBlockTheNextOneNorTheOtherWayRound() {
		UUID ace = this.fixtures.character(READY_TO_PUBLISH, withFruits("Ace", use(this.meraMera, true)));
		UUID sabo = this.fixtures.character(READY_TO_PUBLISH, withFruits("Sabo", use(this.meraMera, false)));
		UUID marco = this.fixtures.character(READY_TO_PUBLISH, withFruits("Marco", use(this.meraMera, true)));
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, ace, 1).version().status()).isEqualTo(PUBLISHED);
		assertThat(this.service.publish(PUBLISHER, CHOPPER, sabo, 1).version().status()).isEqualTo(PUBLISHED);
		assertThat(this.service.publish(PUBLISHER, CHOPPER, marco, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void aCharacterIsNotBlockedByItsOwnVersionOnline() {
		var body = withFruits("Ace", use(this.meraMera, false));
		UUID ace = this.fixtures.character(PUBLISHED, body);
		this.fixtures.characterVersion(ace, 2, READY_TO_PUBLISH, body.toBuilder().japaneseName("x").build());
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, ace, 2).version().status()).isEqualTo(PUBLISHED);
	}

	// ---- What a fruit owes its characters

	@Test
	void aFruitWithOneUserAtATimeDoesNotGoOnlineWhileSeveralCharactersOnlineHaveItCurrentEvenForAnAdministrator() {
		UUID fruit = this.fixtures.devilFruit(READY_TO_PUBLISH, "Gura Gura no Mi", false);
		UUID shirohige = this.fixtures.character(PUBLISHED, withFruits("Shirohige", use(fruit, false)));
		UUID teach = this.fixtures.character(PUBLISHED, withFruits("Teach", use(fruit, false)));
		this.fixtures.character(PUBLISHED, withFruits("Rocks", use(fruit, true)));
		stored();

		var blocked = this.fixtures.devilFruitRules()
			.blockOf(VersionAction.PUBLISH, fruit, this.fixtures.devilFruitVersion(fruit));

		assertThat(blocked).isPresent();
		assertThat(blocked.get().reason()).isEqualTo(BlockReason.DEVIL_FRUIT_IN_USE);
		assertThat(blocked.get().detail()).isEqualTo(Map.of("count", 2L, "characters",
				List.of(Map.of("id", shirohige, "romaji", "Shirohige"), Map.of("id", teach, "romaji", "Teach"))));
	}

	@Test
	void aFruitWithOneUserAtATimeGoesOnlineWithOneCurrentUserAndASharedOneWithAny() {
		UUID gura = this.fixtures.devilFruit(READY_TO_PUBLISH, "Gura Gura no Mi", false);
		this.fixtures.character(PUBLISHED, withFruits("Shirohige", use(gura, false)));
		UUID shared = this.fixtures.devilFruit(READY_TO_PUBLISH, "SMILE Zoan 2", true);
		this.fixtures.character(PUBLISHED, withFruits("Caesar", use(shared, false)));
		this.fixtures.character(PUBLISHED, withFruits("Momonosuke", use(shared, false)));
		stored();

		var rules = this.fixtures.devilFruitRules();
		assertThat(rules.blockOf(VersionAction.PUBLISH, gura, this.fixtures.devilFruitVersion(gura))).isEmpty();
		assertThat(rules.blockOf(VersionAction.PUBLISH, shared, this.fixtures.devilFruitVersion(shared))).isEmpty();
	}

	@Test
	void aFruitIsNotRetiredWhileOnlineCharactersNameItEvenIfTheyLostItEvenForAnAdministrator() {
		UUID ace = this.fixtures.character(PUBLISHED, withFruits("Ace", use(this.meraMera, true)));
		stored();

		var blocked = this.fixtures.devilFruitRules()
			.blockOf(VersionAction.RETIRE, this.meraMera, this.fixtures.devilFruitVersion(this.meraMera));

		assertThat(blocked).isPresent();
		assertThat(blocked.get().reason()).isEqualTo(BlockReason.ONLINE_ITEMS_LINKED);
		assertThat(blocked.get().detail())
			.isEqualTo(Map.of("count", 1L, "characters", List.of(Map.of("id", ace, "romaji", "Ace"))));
	}

	@Test
	void aFruitNoOnlineCharacterNamesIsRetired() {
		this.fixtures.character(READY_TO_PUBLISH, withFruits("Ace", use(this.meraMera, false)));
		stored();

		assertThat(this.fixtures.devilFruitRules()
			.blockOf(VersionAction.RETIRE, this.meraMera, this.fixtures.devilFruitVersion(this.meraMera))).isEmpty();
	}

	// ---- Listing

	@Test
	void theListIsNarrowedByTheFruitTheCharacterCurrentlyHas() {
		UUID sabo = this.fixtures.character(PUBLISHED, withFruits("Sabo", use(this.meraMera, false)));
		this.fixtures.character(PUBLISHED, withFruits("Ace", use(this.meraMera, true)));
		this.fixtures.character(PUBLISHED, this.fixtures.complete("Nami"));
		stored();

		assertThat(search(new ContentFilter(null, null, null, null).withAttribute(Character.DEVIL_FRUIT_ATTRIBUTE,
				this.meraMera)))
			.containsExactly(sabo);
		assertThat(search(
				new ContentFilter(null, null, null, null).withAttribute(Character.DEVIL_FRUIT_ATTRIBUTE, this.smile)))
			.isEmpty();
	}

	@Test
	void aFruitCountsItsCurrentUsersOnlineAndTheLinkableOnesCarryWhetherTheyHaveSeveral() {
		this.fixtures.character(PUBLISHED, withFruits("Sabo", use(this.meraMera, false)));
		this.fixtures.character(PUBLISHED, withFruits("Ace", use(this.meraMera, true)));
		this.fixtures.character(DRAFT, withFruits("Marco", use(this.meraMera, false)));
		stored();
		var links = new DevilFruitLinks(this.devilFruitRepository, this.characterRepository,
				Clock.fixed(CharacterFixtures.NOW, ZoneOffset.UTC));

		assertThat(links.userCounts(List.of(this.meraMera, this.smile))).containsOnly(entry(this.meraMera, 1L));
		assertThat(links.userCounts(List.of())).isEmpty();
		assertThat(links.linkable(null, 0, 10).getContent())
			.extracting(reference -> reference.romaji() + ":" + reference.multipleUsers())
			.containsExactly("Mera Mera no Mi:false", "SMILE Zoan:true");
		assertThat(links.referencesOf(List.of(this.smile)).get(this.smile).names())
			.containsOnly(entry("en", "SMILE Zoan (en)"));
	}

	private Character withFruits(String romaji, FruitUse... uses) {
		return this.fixtures.complete(romaji).toBuilder().devilFruits(List.of(uses)).build();
	}

	private static FruitUse use(UUID devilFruit, boolean former) {
		return new FruitUse(devilFruit, former);
	}

	private List<UUID> search(ContentFilter filter) {
		return this.service.list(EDITOR, CHOPPER, filter, PageRequest.of(0, 20), "en")
			.getContent()
			.stream()
			.map(ContentSummary::contentId)
			.toList();
	}

	private void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

}
