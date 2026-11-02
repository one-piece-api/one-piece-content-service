package dev.onepieceapi.contentservice.service.character;

import static dev.onepieceapi.contentservice.domain.readertime.ParagraphFixtures.discoveredAt;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.character.Alias;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.MarineRank;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.character.MembershipPeriod;
import dev.onepieceapi.contentservice.domain.character.Relative;
import dev.onepieceapi.contentservice.domain.character.RelativeKind;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
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
import dev.onepieceapi.contentservice.service.content.StoredImages;
import dev.onepieceapi.contentservice.service.content.OnlineChapters;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import dev.onepieceapi.exception.web.FieldViolation;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.ADMIN;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.CHOPPER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.EDITOR;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.PUBLISHER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.RETIRER;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.fieldsOf;
import static dev.onepieceapi.contentservice.service.character.CharacterFixtures.violationsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The windows of a character's entries on the reader axis (workflow 3.7.2, 3.7.4; reveals
 * plan R2), against a real PostgreSQL (Testcontainers): every point checked part by part
 * on save against the first appearance and its own window, the periods of a membership
 * against each other, and the same rules again on the numbers online when the character
 * goes online. The character first appears in chapter 10.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class CharacterReaderAxisIntegrationTest {

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
	private TestEntityManager entityManager;

	private CharacterFixtures fixtures;

	private CharacterService service;

	private StoryPoint chapter5;

	private StoryPoint chapter10;

	private StoryPoint chapter20;

	private StoryPoint chapter30;

	private UUID mugiwara;

	private UUID kaigun;

	private UUID baroque;

	@BeforeEach
	void setUp() {
		this.chapter5 = chapter(5);
		this.chapter10 = chapter(10);
		this.chapter20 = chapter(20);
		this.chapter30 = chapter(30);
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
			.firstAppearance(this.chapter10)
			.portrait(StoredImages.portrait(this.imageRepository))
			.build();
		this.service = this.fixtures.service();
		this.mugiwara = this.fixtures.affiliation(PUBLISHED, "Mugiwara no Ichimi", AffiliationKind.PIRATE_CREW);
		this.kaigun = this.fixtures.affiliation(PUBLISHED, "Kaigun", AffiliationKind.NAVY);
		this.baroque = this.fixtures.affiliation(PUBLISHED, "Baroque Works", AffiliationKind.OTHER);
		stored();
	}

	// ---- Saving

	@Test
	void aPointIsRefusedPartByPartBeforeTheFirstAppearanceOffItsAxesOrEndedBeforeItIsDiscovered() {
		UUID episode = OnlineChapters.episode(this.contentRepository, this.episodeRepository, PUBLISHED);
		var written = withAliases("Nami", alias("Early", StoryWindow.from(this.chapter5)),
				alias("Anime", StoryWindow.from(StoryPoint.ofEpisode(episode))),
				alias("Backwards", new StoryWindow(this.chapter30, this.chapter20)),
				alias("Ended off its axis", new StoryWindow(this.chapter20, StoryPoint.ofEpisode(episode))));

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(violationsOf(refused)).extracting(FieldViolation::field, FieldViolation::message)
			.containsExactlyInAnyOrder(
					tuple("aliases[0].discovered.chapter", "must not be before the first appearance"),
					tuple("aliases[1].discovered.episode", "must be on an axis of the first appearance"),
					tuple("aliases[2].ended.chapter", "must not be before discovered"),
					tuple("aliases[3].ended.episode", "must be on an axis of the first appearance"),
					tuple("aliases[3].ended.episode", "must be on an axis where discovered has a part"));
	}

	@Test
	void aPointOnAChapterNothingApprovedOfIsRefusedNamingThePart() {
		UUID draft = OnlineChapters.chapter(this.contentRepository, this.chapterRepository, DRAFT, 40);
		var written = withAliases("Nami",
				alias("Unknown", new StoryWindow(this.chapter10, StoryPoint.ofChapter(draft))));

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(fieldsOf(refused)).containsExactly("aliases[0].ended.chapter");
	}

	@Test
	void dragonIsNamedLuffysFatherLongAfterBothAppearAndRocksIsDiscoveredAlreadyOver() {
		// Luffy first appears in chapter 1; Garp reveals that Dragon is his father in
		// chapter 432 (One Piece Wikipedia, season 9: episode 314). Rocks is first named
		// by
		// Sengoku in chapter 957: the crew is over when the reader learns of it.
		UUID rocks = this.fixtures.affiliation(PUBLISHED, "Rocks Pirates", AffiliationKind.PIRATE_CREW);
		UUID dragon = this.fixtures.character(PUBLISHED, this.fixtures.complete("Dragon"));
		var luffy = this.fixtures.complete("Luffy")
			.toBuilder()
			.firstAppearance(chapter(1))
			.relatives(List.of(new Relative(dragon, RelativeKind.PARENT, StoryWindow.from(chapter(432)))))
			.build();
		StoryPoint rocksNamed = chapter(957);
		var shirohige = this.fixtures.complete("Shirohige")
			.toBuilder()
			.affiliations(List.of(membership(rocks, period(rocksNamed, rocksNamed))))
			.build();

		var luffyContent = this.service.create(EDITOR, CHOPPER, luffy);
		var shirohigeContent = this.service.create(EDITOR, CHOPPER, shirohige);
		stored();

		assertThat(this.service.getVersion(EDITOR, CHOPPER, luffyContent.id(), 1).version().body()).isEqualTo(luffy);
		assertThat(this.service.getVersion(EDITOR, CHOPPER, shirohigeContent.id(), 1).version().body())
			.isEqualTo(shirohige);
	}

	@Test
	void periodsAreRefusedOverlappingOutOfOrderOrOpenBeforeTheLast() {
		var written = this.fixtures.complete("Shanks")
			.toBuilder()
			.affiliations(List.of(
					membership(this.mugiwara, period(this.chapter10, this.chapter30),
							period(this.chapter20, StoryPoint.NONE)),
					membership(this.kaigun, period(this.chapter20, this.chapter30),
							period(this.chapter10, this.chapter20)),
					membership(this.baroque, period(this.chapter10, StoryPoint.NONE),
							period(this.chapter20, StoryPoint.NONE))))
			.build();

		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.service.create(EDITOR, CHOPPER, written));

		assertThat(violationsOf(refused)).extracting(FieldViolation::field, FieldViolation::message)
			.containsExactlyInAnyOrder(
					tuple("affiliations[0].periods[1]", "must not overlap a previous period on the chapter axis"),
					tuple("affiliations[1].periods[1]", "must not start before a previous period on the chapter axis"),
					tuple("affiliations[2].periods[0]", "must have an end: only the last period may have none"),
					tuple("affiliations[2].periods[1]", "must not overlap a previous period on the chapter axis"));
	}

	@Test
	void usoppLeavesAndComesBackAndKobyHasAPeriodPerRankAndBothAreCurrentOnline() {
		// Usopp first appears in chapter 23, leaves the crew after the duel with Luffy in
		// chapter 332 and rejoins in chapter 438 (One Piece Wikipedia, chapter lists and
		// season 9: episode 323). Koby's chapters are illustrative: the chapters of his
		// promotions were not confirmed by a reliable source (doubt recorded in R2).
		StoryPoint usoppMet = chapter(23);
		UUID usopp = this.fixtures.character(READY_TO_PUBLISH, this.fixtures.complete("Usopp")
			.toBuilder()
			.firstAppearance(usoppMet)
			.gallery(this.fixtures.galleryFrom(usoppMet))
			.description(discoveredAt(usoppMet, Map.of("it", "Il cecchino", "en", "The sniper")))
			.affiliations(List
				.of(membership(this.mugiwara, period(usoppMet, chapter(332)), period(chapter(438), StoryPoint.NONE))))
			.build());
		UUID koby = this.fixtures.character(READY_TO_PUBLISH, this.fixtures.complete("Koby")
			.toBuilder()
			.affiliations(List.of(new Membership(this.kaigun,
					List.of(new MembershipPeriod(new StoryWindow(this.chapter10, this.chapter20), Map.of(),
							MarineRank.CHORE_BOY),
							new MembershipPeriod(StoryWindow.from(this.chapter20), Map.of(), MarineRank.CAPTAIN)))))
			.build());
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, usopp, 1).version().status()).isEqualTo(PUBLISHED);
		assertThat(this.service.publish(PUBLISHER, CHOPPER, koby, 1).version().status()).isEqualTo(PUBLISHED);
		stored();

		assertThat(search(Character.AFFILIATION_ATTRIBUTE, this.mugiwara)).containsExactly(usopp);
		assertThat(search(Character.AFFILIATION_KIND_ATTRIBUTE, AffiliationKind.NAVY)).containsExactly(koby);
	}

	@Test
	void saboIsBelievedDeadUntilDressrosaAndHisParagraphsKeepTheirOrderAndWindowsOnlineAndInEveryVersion() {
		// Sabo first appears in chapter 583, believed dead; the reader learns he is alive
		// in chapter 744 (decision 4 of R3: confirmed numbers only).
		StoryPoint met = chapter(583);
		StoryPoint alive = chapter(744);
		List<Paragraph> description = List.of(
				new Paragraph(Map.of("it", "Creduto morto", "en", "Believed dead"), new StoryWindow(met, alive)),
				new Paragraph(
						Map.of("it", "Vivo, nell'Armata Rivoluzionaria", "en", "Alive, in the Revolutionary Army"),
						StoryWindow.from(alive)));
		UUID sabo = this.fixtures.character(READY_TO_PUBLISH,
				this.fixtures.complete("Sabo")
					.toBuilder()
					.firstAppearance(met)
					.gallery(this.fixtures.galleryFrom(met))
					.description(description)
					.build());
		stored();

		assertThat(this.service.publish(PUBLISHER, CHOPPER, sabo, 1).version().status()).isEqualTo(PUBLISHED);
		stored();
		assertThat(descriptionOf(sabo, 1)).isEqualTo(description);

		this.service.openNewVersion(EDITOR, CHOPPER, sabo, 1);
		stored();
		assertThat(descriptionOf(sabo, 2)).isEqualTo(description);

		this.service.retire(RETIRER, CHOPPER, sabo, 1);
		this.service.restore(ADMIN, CHOPPER, sabo, 1);
		stored();
		assertThat(descriptionOf(sabo, 1)).isEqualTo(description);
	}

	@Test
	void aMembershipWithoutPeriodsIsSavedButNotSentToReview() {
		var content = this.service.create(EDITOR, CHOPPER,
				this.fixtures.complete("Vivi")
					.toBuilder()
					.affiliations(List.of(new Membership(this.baroque, List.of())))
					.build());
		stored();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.service.submit(EDITOR, CHOPPER, content.id(), 1));

		assertThat(fieldsOf(refused)).containsExactly("affiliations[0].periods");
	}

	// ---- Going online

	@Test
	void aCharacterDoesNotGoOnlineWhileAChapterOfAWindowIsOnlyApprovedNamingThePartEvenForAnAdministrator() {
		UUID approved = OnlineChapters.chapter(this.contentRepository, this.chapterRepository, READY_TO_PUBLISH, 40);
		UUID nami = this.fixtures.character(READY_TO_PUBLISH,
				withAliases("Nami", alias("Later", StoryWindow.from(StoryPoint.ofChapter(approved)))));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(ADMIN, CHOPPER, nami, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("field", "aliases[0].discovered.chapter", "chapterId", approved, "chapterNumber", 40));
	}

	@Test
	void aCharacterDoesNotGoOnlineWhileTheNumbersOnlinePutAPointOutOfRangeEvenForAnAdministrator() {
		// Approved as chapter 25, still online as chapter 7: before the first appearance.
		UUID renumbered = OnlineChapters.chapter(this.contentRepository, this.chapterRepository, PUBLISHED, 7);
		OnlineChapters.chapterVersion(this.chapterRepository, renumbered, 2, READY_TO_PUBLISH, 25);
		UUID nami = this.fixtures.character(READY_TO_PUBLISH,
				withAliases("Nami", alias("Renumbered", StoryWindow.from(StoryPoint.ofChapter(renumbered)))));
		stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.service.publish(ADMIN, CHOPPER, nami, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.STORY_POINT_OUT_OF_RANGE);
		assertThat(refused.getDetails().get("detail")).isEqualTo(
				Map.of("field", "aliases[0].discovered.chapter", "message", "must not be before the first appearance"));
	}

	private StoryPoint chapter(int number) {
		return StoryPoint
			.ofChapter(OnlineChapters.chapter(this.contentRepository, this.chapterRepository, PUBLISHED, number));
	}

	private List<Paragraph> descriptionOf(UUID character, int version) {
		return this.service.getVersion(EDITOR, CHOPPER, character, version).version().body().description();
	}

	private Character withAliases(String romaji, Alias... aliases) {
		return this.fixtures.complete(romaji).toBuilder().aliases(List.of(aliases)).build();
	}

	private static Alias alias(String name, StoryWindow window) {
		return new Alias(name, window);
	}

	private static Membership membership(UUID affiliation, MembershipPeriod... periods) {
		return new Membership(affiliation, List.of(periods));
	}

	private static MembershipPeriod period(StoryPoint discovered, StoryPoint ended) {
		return new MembershipPeriod(new StoryWindow(discovered, ended), Map.of(), null);
	}

	private List<UUID> search(String attribute, Object value) {
		return this.service
			.list(EDITOR, CHOPPER, new ContentFilter(null, null, null, null).withAttribute(attribute, value),
					PageRequest.of(0, 20), "en")
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
