package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.MarineRank;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.character.MembershipPeriod;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ImageRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.service.affiliation.AffiliationLinks;
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
import dev.onepieceapi.contentservice.service.content.StoredImages;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
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
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.RETIRED;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.ADMIN;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.EDITOR;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.PUBLISHER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.RETIRER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.fieldsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The memberships of a character (implementation plan of the Character, C8, D10), against
 * a real PostgreSQL (Testcontainers): kept in their order with their periods, role and
 * rank; the save refusing what it should, entry by entry; what a character needs to go
 * online with them and what an affiliation owes its members; the list narrowed by
 * affiliation and kind. Whoever breaks a rule is refused with the reason, even an
 * administrator.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class CharacterMembershipsIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	@Autowired
	private CharacterVersionRepository characterRepository;

	@Autowired
	private AffiliationVersionRepository affiliationRepository;

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
	private ImageRepository imageRepository;

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

	private UUID mugiwara;

	private UUID kaigun;

	/** A chapter online after the first appearance: where a former membership ends. */
	private StoryPoint later;

	@BeforeEach
	void setUp() {
		this.fixtures = CharacterFixtures.builder()
			.contents(this.contentRepository)
			.characters(this.characterRepository)
			.species(this.speciesRepository)
			.affiliations(this.affiliationRepository)
			.bounties(this.bountyRepository)
			.chapters(this.chapterRepository)
			.episodes(this.episodeRepository)
			.contentVersions(this.contentVersionRepository)
			.languages(this.languageRepository)
			.auditLog(this.auditLogRepository)
			.firstAppearance(OnlineChapters.onlineChapter(this.contentRepository, this.chapterRepository))
			.portrait(StoredImages.portrait(this.imageRepository))
			.build();
		this.service = this.fixtures.service();
		this.mugiwara = this.fixtures.affiliation(PUBLISHED, "Mugiwara no Ichimi", AffiliationKind.PIRATE_CREW);
		this.kaigun = this.fixtures.affiliation(PUBLISHED, "Kaigun", AffiliationKind.NAVY);
		this.later = StoryPoint
			.ofChapter(OnlineChapters.chapter(this.contentRepository, this.chapterRepository, PUBLISHED, 10));
		stored();
	}

	// ---- Saving

	@Test
	void aNewCharacterKeepsItsMembershipsInTheirOrderWithTheirPeriodRoleAndRank() {
		var captain = current(this.mugiwara, Map.of("en", " Captain ", "it", "Capitano"), null);
		var koby = former(this.kaigun, Map.of(), MarineRank.CAPTAIN);
		var written = this.fixtures.complete("Koby").toBuilder().affiliations(List.of(captain, koby)).build();

		var content = this.service.create(EDITOR, CHOPPER, written);
		stored();

		var body = this.service.getVersion(EDITOR, CHOPPER, content.id(), 1).version().body();
		assertThat(body.affiliations())
			.containsExactly(current(this.mugiwara, Map.of("en", "Captain", "it", "Capitano"), null), koby);
	}

	@Test
	void reorderingAndShorteningTheMembershipsOfADraftKeepsTheNewList() {
		var first = current(this.mugiwara, Map.of("en", "Navigator", "it", "Navigatrice"), null);
		var second = former(this.kaigun, Map.of(), MarineRank.ENSIGN);
		var written = this.fixtures.complete("Nami").toBuilder().affiliations(List.of(first, second)).build();
		var content = this.service.create(EDITOR, CHOPPER, written);
		stored();

		this.service.edit(EDITOR, CHOPPER, content.id(), 1,
				written.toBuilder().affiliations(List.of(second, first)).build());
		stored();
		// What the commit would check: the affiliations, swapped row by row, end up
		// distinct.
		this.jdbc.execute("set constraints uq_character_version_affiliation immediate");
		assertThat(this.service.getVersion(EDITOR, CHOPPER, content.id(), 1).version().body().affiliations())
			.containsExactly(second, first);

		this.jdbc.execute("set constraints uq_character_version_affiliation deferred");
		this.service.edit(EDITOR, CHOPPER, content.id(), 1, written.toBuilder().affiliations(List.of(first)).build());
		stored();
		assertThat(this.service.getVersion(EDITOR, CHOPPER, content.id(), 1).version().body().affiliations())
			.containsExactly(first);
	}

	@Test
	void aNewVersionStartsFromItsBaseWithEveryMembershipInItsOrder() {
		var body = this.fixtures.complete("Koby")
			.toBuilder()
			.affiliations(List.of(current(this.kaigun, Map.of("en", "Hero"), MarineRank.CAPTAIN),
					former(this.mugiwara, Map.of(), null)))
			.build();
		UUID koby = this.fixtures.character(PUBLISHED, body);
		stored();

		this.service.openNewVersion(EDITOR, CHOPPER, koby, 1);
		stored();

		assertThat(this.service.getVersion(EDITOR, CHOPPER, koby, 2).version().body()).isEqualTo(body);
	}

	@Test
	void anAffiliationNamedTwiceIsRefusedByTheIndexOfItsRepeat() {
		var written = Character.builder()
			.romaji("Shanks")
			.affiliations(List.of(current(this.mugiwara, Map.of(), null), current(this.kaigun, Map.of(), null),
					former(this.mugiwara, Map.of(), null)))
			.build();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactly("affiliations[2].affiliation");
	}

	@Test
	void anAffiliationNothingApprovedOfIsRefusedByTheIndexOfItsEntry() {
		UUID draft = this.fixtures.affiliation(DRAFT, "Akagami", AffiliationKind.PIRATE_CREW);
		stored();
		var written = Character.builder()
			.romaji("Shanks")
			.firstAppearance(this.fixtures.fromStart().discovered())
			.affiliations(List.of(current(this.mugiwara, Map.of(), null), current(draft, Map.of(), null),
					current(UUID.randomUUID(), Map.of(), null)))
			.build();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactly("affiliations[1].affiliation", "affiliations[2].affiliation");
	}

	@Test
	void aRankIsAllowedOnlyInAnAffiliationThatIsANavyOneByItsLatestApprovedVersion() {
		UUID unset = this.fixtures.affiliation(PUBLISHED, "Seifu", null);
		stored();
		var written = Character.builder()
			.romaji("Smoker")
			.firstAppearance(this.fixtures.fromStart().discovered())
			.affiliations(List.of(current(this.kaigun, Map.of(), MarineRank.VICE_ADMIRAL),
					current(this.mugiwara, Map.of(), MarineRank.ADMIRAL), current(unset, Map.of(), MarineRank.ENSIGN)))
			.build();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactly("affiliations[1].periods[0].marineRank",
				"affiliations[2].periods[0].marineRank");
	}

	@Test
	void aRoleWrittenInOneLanguageIsRequiredInEveryOtherToSubmit() {
		var content = this.service.create(EDITOR, CHOPPER,
				this.fixtures.complete("Nami")
					.toBuilder()
					.affiliations(List.of(current(this.mugiwara, Map.of("en", "Navigator"), null),
							current(this.kaigun, Map.of(), null)))
					.build());
		stored();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.service.submit(EDITOR, CHOPPER, content.id(), 1));

		assertThat(fieldsOf(refused)).containsExactly("affiliations[0].periods[0].role[it]");
	}

	@Test
	void aLanguageOnlyARoleIsWrittenInIsInUse() {
		this.jdbc.execute("insert into language (code, name) values ('fr', 'Français')");
		this.fixtures.character(DRAFT,
				Character.builder()
					.romaji("Nami")
					.affiliations(List.of(current(this.mugiwara, Map.of("fr", "Navigatrice"), null)))
					.build());
		stored();

		assertThat(this.characterRepository.existsByLanguage("fr")).isTrue();
	}

	@Test
	void theDatabaseRefusesAnAffiliationThatIsNoAffiliation() {
		UUID ningen = this.fixtures.species(PUBLISHED, "Ningen");
		this.fixtures.character(DRAFT,
				this.fixtures.complete("Nami")
					.toBuilder()
					.affiliations(List.of(current(ningen, Map.of(), null)))
					.build());

		assertThatThrownBy(this::stored).isInstanceOf(ConstraintViolationException.class)
			.hasMessageContaining("fk_character_version_affiliation");
	}

	@Test
	void theDatabaseRefusesTheSameAffiliationTwiceInAVersionOnceTheTransactionChecksIt() {
		this.fixtures.character(DRAFT,
				this.fixtures.complete("Nami")
					.toBuilder()
					.affiliations(
							List.of(current(this.mugiwara, Map.of(), null), former(this.mugiwara, Map.of(), null)))
					.build());
		stored();

		assertThatThrownBy(() -> this.jdbc.execute("set constraints uq_character_version_affiliation immediate"))
			.hasMessageContaining("uq_character_version_affiliation");
	}

	// ---- Going online and retiring

	@Test
	void aCharacterGoesOnlineWithItsMembershipsWhileEveryAffiliationIsAndARankIsInANavyOne() {
		UUID koby = this.fixtures.character(READY_TO_PUBLISH, this.fixtures.complete("Koby")
			.toBuilder()
			.affiliations(
					List.of(current(this.kaigun, Map.of(), MarineRank.CAPTAIN), former(this.mugiwara, Map.of(), null)))
			.build());
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, koby, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void aCharacterDoesNotGoOnlineWhileAnAffiliationOfItsListIsNotNamingItsIndexEvenForAnAdministrator() {
		UUID akagami = this.fixtures.affiliation(ARCHIVED, "Akagami", AffiliationKind.PIRATE_CREW);
		UUID shanks = this.fixtures.character(READY_TO_PUBLISH,
				this.fixtures.complete("Shanks")
					.toBuilder()
					.affiliations(List.of(current(this.mugiwara, Map.of(), null), current(akagami, Map.of(), null)))
					.build());
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(ADMIN, CHOPPER, shanks, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("affiliationsIndex", 1, "affiliationId", akagami, "affiliationRomaji", "Akagami"));
	}

	@Test
	void aRankDoesNotGoOnlineInAnAffiliationWhoseOnlineVersionIsNotANavyOneEvenForAnAdministrator() {
		// The save saw the Navy; the affiliation went online as another kind since.
		UUID koby = this.fixtures.character(READY_TO_PUBLISH,
				this.fixtures.complete("Koby")
					.toBuilder()
					.affiliations(List.of(current(this.mugiwara, Map.of(), MarineRank.CAPTAIN)))
					.build());
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(ADMIN, CHOPPER, koby, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.MARINE_RANK_NOT_ALLOWED);
		assertThat(refused.getDetails().get("detail")).isEqualTo(Map.of("affiliationsIndex", 0, "periodsIndex", 0,
				"affiliationId", this.mugiwara, "affiliationRomaji", "Mugiwara no Ichimi"));
	}

	@Test
	void anAffiliationIsNotRetiredWhileOnlineCharactersNameItEvenIfTheyLeftItEvenForAnAdministrator() {
		UUID luffy = this.fixtures.character(PUBLISHED,
				this.fixtures.complete("Luffy")
					.toBuilder()
					.affiliations(List.of(former(this.mugiwara, Map.of(), null)))
					.build());
		this.fixtures.character(READY_TO_PUBLISH,
				this.fixtures.complete("Zoro")
					.toBuilder()
					.affiliations(List.of(current(this.mugiwara, Map.of(), null)))
					.build());
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.fixtures.affiliationService().retire(ADMIN, CHOPPER, this.mugiwara, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.ONLINE_ITEMS_LINKED);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("count", 1L, "characters", List.of(Map.of("id", luffy, "romaji", "Luffy"))));
	}

	@Test
	void anAffiliationNoOnlineCharacterNamesIsRetired() {
		this.fixtures.character(READY_TO_PUBLISH,
				this.fixtures.complete("Zoro")
					.toBuilder()
					.affiliations(List.of(current(this.mugiwara, Map.of(), null)))
					.build());
		stored();

		assertThat(this.fixtures.affiliationService().retire(RETIRER, CHOPPER, this.mugiwara, 1).version().status())
			.isEqualTo(RETIRED);
	}

	@Test
	void anAffiliationVersionIsNotANavyOneIsRefusedWhileOnlineCharactersHoldARankInItEvenForAnAdministrator() {
		UUID koby = this.fixtures.character(PUBLISHED,
				this.fixtures.complete("Koby")
					.toBuilder()
					.affiliations(List.of(current(this.kaigun, Map.of(), MarineRank.CAPTAIN)))
					.build());
		this.fixtures.affiliationVersion(this.kaigun, 2, READY_TO_PUBLISH, "Kaigun", AffiliationKind.OTHER);
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.fixtures.affiliationService().publish(ADMIN, CHOPPER, this.kaigun, 2));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.MARINE_RANK_IN_USE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("count", 1L, "characters", List.of(Map.of("id", koby, "romaji", "Koby"))));
	}

	@Test
	void anAffiliationVersionChangesItsKindWhenNoOnlineCharacterHoldsARankInIt() {
		this.fixtures.character(PUBLISHED,
				this.fixtures.complete("Koby")
					.toBuilder()
					.affiliations(List.of(current(this.kaigun, Map.of(), null)))
					.build());
		this.fixtures.character(READY_TO_PUBLISH,
				this.fixtures.complete("Helmeppo")
					.toBuilder()
					.affiliations(List.of(current(this.kaigun, Map.of(), MarineRank.ENSIGN)))
					.build());
		this.fixtures.affiliationVersion(this.kaigun, 2, READY_TO_PUBLISH, "Kaigun", AffiliationKind.OTHER);
		stored();

		assertThat(this.fixtures.affiliationService().publish(PUBLISHER, CHOPPER, this.kaigun, 2).version().status())
			.isEqualTo(PUBLISHED);
	}

	// ---- Listing

	@Test
	void theListIsNarrowedByAffiliationAndByKindOfTheAffiliationsTheCharacterCurrentlyBelongsTo() {
		UUID luffy = this.fixtures.character(PUBLISHED,
				withMemberships("Luffy", current(this.mugiwara, Map.of(), null)));
		this.fixtures.character(PUBLISHED, withMemberships("Zoro", former(this.mugiwara, Map.of(), null)));
		UUID koby = this.fixtures.character(PUBLISHED,
				withMemberships("Koby", current(this.kaigun, Map.of(), MarineRank.CAPTAIN)));
		this.fixtures.character(PUBLISHED, this.fixtures.complete("Nami"));
		stored();

		assertThat(search(new ContentFilter(null, null, null, null).withAttribute(Character.AFFILIATION_ATTRIBUTE,
				this.mugiwara)))
			.containsExactly(luffy);
		assertThat(search(new ContentFilter(null, null, null, null).withAttribute(Character.AFFILIATION_KIND_ATTRIBUTE,
				AffiliationKind.PIRATE_CREW)))
			.containsExactly(luffy);
		assertThat(search(new ContentFilter(null, null, null, null).withAttribute(Character.AFFILIATION_KIND_ATTRIBUTE,
				AffiliationKind.NAVY)))
			.containsExactly(koby);
		assertThat(search(new ContentFilter(null, null, null, null).withAttribute(Character.AFFILIATION_KIND_ATTRIBUTE,
				AffiliationKind.OTHER)))
			.isEmpty();
	}

	@Test
	void theKindFilterReadsTheLatestApprovedVersionOfTheAffiliation() {
		UUID koby = this.fixtures.character(PUBLISHED, withMemberships("Koby", current(this.kaigun, Map.of(), null)));
		this.fixtures.affiliationVersion(this.kaigun, 2, READY_TO_PUBLISH, "Kaigun", AffiliationKind.OTHER);
		stored();

		assertThat(search(new ContentFilter(null, null, null, null).withAttribute(Character.AFFILIATION_KIND_ATTRIBUTE,
				AffiliationKind.OTHER)))
			.containsExactly(koby);
		assertThat(search(new ContentFilter(null, null, null, null).withAttribute(Character.AFFILIATION_KIND_ATTRIBUTE,
				AffiliationKind.NAVY)))
			.isEmpty();
	}

	@Test
	void anAffiliationCountsItsCurrentMembersOnlineAndTheLinkableOnesCarryTheirKind() {
		this.fixtures.character(PUBLISHED, withMemberships("Luffy", current(this.mugiwara, Map.of(), null)));
		this.fixtures.character(PUBLISHED, withMemberships("Zoro", former(this.mugiwara, Map.of(), null)));
		this.fixtures.character(PUBLISHED, withMemberships("Nami", current(this.mugiwara, Map.of(), null)));
		this.fixtures.character(DRAFT, withMemberships("Usopp", current(this.mugiwara, Map.of(), null)));
		stored();
		var links = new AffiliationLinks(this.affiliationRepository, this.characterRepository,
				Clock.fixed(CharacterFixtures.NOW, ZoneOffset.UTC));

		assertThat(links.memberCounts(List.of(this.mugiwara, this.kaigun))).containsOnly(entry(this.mugiwara, 2L));
		assertThat(links.memberCounts(List.of())).isEmpty();
		assertThat(links.linkable(null, 0, 10).getContent())
			.extracting(reference -> reference.romaji() + ":" + reference.kind())
			.containsExactly("Kaigun:NAVY", "Mugiwara no Ichimi:PIRATE_CREW");
		assertThat(links.referencesOf(List.of(this.kaigun)).get(this.kaigun).names())
			.containsOnly(entry("en", "Kaigun (en)"), entry("it", "Kaigun (it)"));
	}

	/** A membership of a single period, current. */
	private Membership current(UUID affiliation, Map<String, String> role, MarineRank rank) {
		return this.fixtures.membership(affiliation, role, rank);
	}

	/** A membership of a single period, ended at {@link #later}. */
	private Membership former(UUID affiliation, Map<String, String> role, MarineRank rank) {
		var window = new StoryWindow(this.fixtures.fromStart().discovered(), this.later);
		return new Membership(affiliation, List.of(new MembershipPeriod(window, role, rank)));
	}

	private Character withMemberships(String romaji, Membership... memberships) {
		return this.fixtures.complete(romaji).toBuilder().affiliations(List.of(memberships)).build();
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
