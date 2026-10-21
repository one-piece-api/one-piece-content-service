package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.domain.story.Release;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.story.Volume;
import dev.onepieceapi.contentservice.domain.story.VolumeTranslation;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ImageRepository;
import dev.onepieceapi.contentservice.persistence.repository.JpaImageStore;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import dev.onepieceapi.contentservice.service.image.ContentImages;
import dev.onepieceapi.contentservice.service.image.ImageNormalizer;
import dev.onepieceapi.contentservice.service.image.PngImageEncoder;
import dev.onepieceapi.contentservice.service.validation.ArcValidator;
import dev.onepieceapi.contentservice.service.validation.ChapterValidator;
import dev.onepieceapi.contentservice.service.validation.EpisodeValidator;
import dev.onepieceapi.contentservice.service.validation.ImageValidator;
import dev.onepieceapi.contentservice.service.validation.VolumeValidator;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.service.story.StoryFixtures.fieldsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Creating a volume, a chapter or an episode and editing its draft against a real
 * PostgreSQL (Testcontainers) - the same workflow as every content - and what is their
 * own (implementation plan of the story structure, S2-S4, S13): the number as the natural
 * key, unique among the contents of the entity across every version, gaps allowed; a
 * title that may repeat; the arc and the volume a chapter points to, and the arc of an
 * episode if any, which must have passed review; a page count of 1 or more; and one day
 * per known country in the releases.
 * <p>
 * Seeded for every test: the arc Romance Dawn, the volume 1, a chapter and an episode
 * (their numbers are asked of the fixtures), all online.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class StoryItemsDraftIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private static final LocalDate DAY = LocalDate.of(1997, 12, 24);

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Autowired
	private DevilFruitTypeVersionRepository typeRepository;

	@Autowired
	private BountyVersionRepository bountyRepository;

	@Autowired
	private AffiliationVersionRepository affiliationRepository;

	@Autowired
	private SpeciesVersionRepository speciesRepository;

	@Autowired
	private CharacterVersionRepository characterRepository;

	@Autowired
	private DevilFruitVersionRepository fruitRepository;

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
	private ImageRepository imageRepository;

	@Autowired
	private TestEntityManager entityManager;

	private StoryFixtures fixtures;

	private VolumeService volumes;

	private ChapterService chapters;

	private EpisodeService episodes;

	private UUID romanceDawn;

	private int romanceDawnNumber;

	private UUID volumeOne;

	private int volumeOneNumber;

	private UUID chapterOne;

	private int chapterOneNumber;

	private UUID episodeOne;

	private int episodeOneNumber;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		var images = new ContentImages(new ImageValidator(), new ImageNormalizer(new PngImageEncoder()),
				new JpaImageStore(this.imageRepository, clock), StoryFixtures.VOLUME_IMAGES);
		this.volumes = new VolumeService(this.contentVersionRepository, this.contentRepository, auditLogService, clock,
				images,
				new VolumeDefinition(this.volumeRepository,
						new VolumeValidator(this.volumeRepository, this.languageRepository),
						new VolumeRules(this.contentRepository, this.chapterRepository, new RulesProperties(5))));
		this.chapters = new ChapterService(this.chapterRepository, this.contentVersionRepository,
				this.contentRepository,
				new ChapterValidator(this.chapterRepository, this.languageRepository, this.arcRepository,
						this.volumeRepository),
				new ChapterRules(this.contentRepository, this.arcRepository, this.volumeRepository,
						this.fruitRepository, this.typeRepository, this.bountyRepository, this.affiliationRepository,
						this.speciesRepository, this.characterRepository, new RulesProperties(5)),
				auditLogService, clock);
		this.episodes = new EpisodeService(this.episodeRepository, this.contentVersionRepository,
				this.contentRepository,
				new EpisodeValidator(this.episodeRepository, this.languageRepository, this.arcRepository),
				new EpisodeRules(this.contentRepository, this.arcRepository, this.fruitRepository, this.typeRepository,
						this.bountyRepository, this.affiliationRepository, this.speciesRepository,
						this.characterRepository, new RulesProperties(5)),
				auditLogService, clock);

		this.fixtures = new StoryFixtures(this.entityManager, this.sagaRepository, this.arcRepository,
				this.volumeRepository, this.chapterRepository, this.episodeRepository, this.chopper);
		this.romanceDawn = this.fixtures.arc(this.fixtures.saga(PUBLISHED), PUBLISHED);
		this.romanceDawnNumber = this.fixtures.lastNumber();
		this.volumeOne = this.fixtures.volume(PUBLISHED);
		this.volumeOneNumber = this.fixtures.lastNumber();
		this.chapterOne = this.fixtures.chapter(this.romanceDawn, this.volumeOne, PUBLISHED);
		this.chapterOneNumber = this.fixtures.lastNumber();
		this.episodeOne = this.fixtures.episode(this.romanceDawn, PUBLISHED);
		this.episodeOneNumber = this.fixtures.lastNumber();
		this.fixtures.stored();
	}

	@Test
	void aNewChapterIsBornWithADraftSayingWhatWasNormalized() {
		var written = Chapter.builder()
			.number(500)
			.arcContentId(this.romanceDawn)
			.volumeContentId(this.volumeOne)
			.japaneseTitle("  ROMANCE DAWN ")
			.pageCount(54)
			.releases(List.of(new Release("it", DAY.plusYears(4)), new Release(" jp ", DAY)))
			.translations(Map.of("it", new StoryTranslation(" L'alba ", "  ")))
			.build();

		var content = this.chapters.create(EDITOR, this.nami, written);
		this.fixtures.stored();

		var version = this.chapters.getVersion(EDITOR, this.nami, content.id(), 1).version();
		assertThat(version.status()).isEqualTo(DRAFT);
		assertThat(version.author()).isEqualTo(this.nami);
		assertThat(version.body()).isEqualTo(Chapter.builder()
			.number(500)
			.arcContentId(this.romanceDawn)
			.volumeContentId(this.volumeOne)
			.japaneseTitle("ROMANCE DAWN")
			.pageCount(54)
			.releases(List.of(new Release("JP", DAY), new Release("IT", DAY.plusYears(4))))
			.translations(Map.of("it", new StoryTranslation("L'alba", null)))
			.build());
	}

	@Test
	void aChapterDraftMayHaveNeitherArcNorVolumeYetAndNumberZeroIsAValidNumber() {
		var content = this.chapters.create(EDITOR, this.nami, Chapter.builder().number(0).build());
		this.fixtures.stored();

		var body = this.chapters.getVersion(EDITOR, this.nami, content.id(), 1).version().body();
		assertThat(body.number()).isZero();
		assertThat(body.arcContentId()).isNull();
		assertThat(body.volumeContentId()).isNull();
	}

	@Test
	void aNewEpisodeAndAVolumeAreBornWithADraftSayingWhatWasNormalized() {
		var episode = this.episodes.create(EDITOR, this.nami,
				Episode.builder()
					.number(900)
					.broadcasts(List.of(new Release("jp", DAY)))
					.japaneseTitle(" Ore wa Luffy! ")
					.build());
		var volume = this.volumes.create(EDITOR, this.nami,
				Volume.builder()
					.number(900)
					.releases(List.of(new Release("jp", DAY)))
					.translations(Map.of("it", new VolumeTranslation(" L'alba ")))
					.build());
		this.fixtures.stored();

		var episodeBody = this.episodes.getVersion(EDITOR, this.nami, episode.id(), 1).version().body();
		var volumeBody = this.volumes.getVersion(EDITOR, this.nami, volume.id(), 1).version().body();
		assertThat(episodeBody.broadcasts()).containsExactly(new Release("JP", DAY));
		assertThat(episodeBody.japaneseTitle()).isEqualTo("Ore wa Luffy!");
		assertThat(episodeBody.arcContentId()).isNull();
		assertThat(volumeBody.releases()).containsExactly(new Release("JP", DAY));
		assertThat(volumeBody.translations()).containsEntry("it", new VolumeTranslation("L'alba"));
	}

	@Test
	void aNumberAnotherContentOfTheEntityHasIsRefusedForEachEntity() {
		var chapter = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.chapters.create(EDITOR, this.nami, Chapter.builder().number(this.chapterOneNumber).build()));
		var episode = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.episodes.create(EDITOR, this.nami, Episode.builder().number(this.episodeOneNumber).build()));
		var volume = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.volumes.create(EDITOR, this.nami, Volume.builder().number(this.volumeOneNumber).build()));

		assertThat(fieldsOf(chapter)).containsExactly("number");
		assertThat(fieldsOf(episode)).containsExactly("number");
		assertThat(fieldsOf(volume)).containsExactly("number");
	}

	@Test
	void aNumberOfAnOldVersionIsStillTakenAndAnotherEntitysNumberIsFree() {
		this.fixtures.chapter(this.romanceDawn, null, VersionStatus.SUPERSEDED);
		int superseded = this.fixtures.lastNumber();
		this.fixtures.stored();

		var refused = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.chapters.create(EDITOR, this.nami, Chapter.builder().number(superseded).build()));
		var episode = this.episodes.create(EDITOR, this.nami, Episode.builder().number(superseded).build());

		assertThat(fieldsOf(refused)).containsExactly("number");
		assertThat(episode.versions()).hasSize(1);
	}

	@Test
	void aContentKeepsItsOwnNumberInANewVersion() {
		this.chapters.openNewVersion(EDITOR, this.nami, this.chapterOne, 1);
		this.fixtures.stored();

		var edited = this.chapters.edit(EDITOR, this.nami, this.chapterOne, 2,
				StoryFixtures.completeChapter(this.chapterOneNumber, this.romanceDawn, this.volumeOne));

		assertThat(edited.version().body().number()).isEqualTo(this.chapterOneNumber);
	}

	@Test
	void aTitleAnotherContentHasInTheSameLanguageIsAllowedForEachEntity() {
		var titles = Map.of("en", new StoryTranslation("Chapter", "d"));

		var chapter = this.chapters.create(EDITOR, this.nami,
				Chapter.builder().number(600).translations(titles).build());
		var episode = this.episodes.create(EDITOR, this.nami,
				Episode.builder().number(600).translations(titles).build());
		var volume = this.volumes.create(EDITOR, this.nami,
				Volume.builder().number(600).translations(Map.of("en", new VolumeTranslation("Volume 1"))).build());

		assertThat(chapter.versions()).hasSize(1);
		assertThat(episode.versions()).hasSize(1);
		assertThat(volume.versions()).hasSize(1);
	}

	@Test
	void aTitleIsStillUniqueForAnArc() {
		var arcTitle = Map.of("en", new StoryTranslation("Arc", "d"));
		this.fixtures.stored();
		var arcs = arcService();

		var refused = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> arcs.create(EDITOR, this.nami, new Arc(700, null, arcTitle)));

		assertThat(fieldsOf(refused)).containsExactly("translations[en].title");
	}

	@Test
	void aChapterArcMustHaveBeenApprovedAndBeAnArc() {
		UUID draftArc = this.fixtures.arc(null, DRAFT);
		this.fixtures.stored();

		for (UUID notLinkable : List.of(draftArc, UUID.randomUUID(), this.volumeOne)) {
			var refused = catchThrowableOfType(ValueInvalidException.class, () -> this.chapters.create(EDITOR,
					this.nami, Chapter.builder().number(800).arcContentId(notLinkable).build()));

			assertThat(fieldsOf(refused)).containsExactly("arc");
		}
	}

	@Test
	void aChapterVolumeMustHaveBeenApprovedAndBeAVolumeButMayBeLeftOut() {
		UUID draftVolume = this.fixtures.volume(DRAFT);
		this.fixtures.stored();

		for (UUID notLinkable : List.of(draftVolume, UUID.randomUUID(), this.romanceDawn)) {
			var refused = catchThrowableOfType(ValueInvalidException.class, () -> this.chapters.create(EDITOR,
					this.nami,
					Chapter.builder().number(800).arcContentId(this.romanceDawn).volumeContentId(notLinkable).build()));

			assertThat(fieldsOf(refused)).containsExactly("volume");
		}
		assertThat(this.chapters.create(EDITOR, this.nami, Chapter.builder().number(800).build()).versions())
			.hasSize(1);
	}

	@ParameterizedTest
	@EnumSource(names = { "READY_TO_PUBLISH", "ARCHIVED", "RETIRED", "SUPERSEDED" })
	void anArcWithAnApprovedVersionCanBeLinkedWhateverBecameOfIt(VersionStatus status) {
		UUID arc = this.fixtures.arc(null, status);
		this.fixtures.stored();

		var chapter = this.chapters.create(EDITOR, this.nami, Chapter.builder().number(800).arcContentId(arc).build());
		var episode = this.episodes.create(EDITOR, this.nami, Episode.builder().number(800).arcContentId(arc).build());

		assertThat(chapter.versions()).hasSize(1);
		assertThat(episode.versions()).hasSize(1);
	}

	@Test
	void anEpisodeArcMustHaveBeenApprovedAndBeAnArcButMayBeLeftOut() {
		UUID draftArc = this.fixtures.arc(null, DRAFT);
		this.fixtures.stored();

		for (UUID notLinkable : List.of(draftArc, UUID.randomUUID(), this.volumeOne)) {
			var refused = catchThrowableOfType(ValueInvalidException.class, () -> this.episodes.create(EDITOR,
					this.nami, Episode.builder().number(800).arcContentId(notLinkable).build()));

			assertThat(fieldsOf(refused)).containsExactly("arc");
		}
	}

	@Test
	void theLinksAreCheckedAgainWhenTheDraftIsEdited() {
		var draft = this.chapters.create(EDITOR, this.nami, Chapter.builder().number(800).build());
		this.fixtures.stored();

		var refused = catchThrowableOfType(ValueInvalidException.class, () -> this.chapters.edit(EDITOR, this.nami,
				draft.id(), 1, Chapter.builder().number(800).arcContentId(UUID.randomUUID()).build()));

		assertThat(fieldsOf(refused)).containsExactly("arc");
	}

	@Test
	void aPageCountBelowOneIsRefusedAndOneIsFine() {
		for (int pageCount : List.of(0, -3)) {
			var refused = catchThrowableOfType(ValueInvalidException.class, () -> this.chapters.create(EDITOR,
					this.nami, Chapter.builder().number(801).pageCount(pageCount).build()));

			assertThat(fieldsOf(refused)).containsExactly("pageCount");
		}
		assertThat(
				this.chapters.create(EDITOR, this.nami, Chapter.builder().number(801).pageCount(1).build()).versions())
			.hasSize(1);
	}

	@Test
	void aCountryWithTwoDatesIsRefusedNamingTheSecondOneForEachList() {
		var twice = List.of(new Release("JP", DAY), new Release("jp", DAY.plusDays(1)));

		var chapter = catchThrowableOfType(ValueInvalidException.class,
				() -> this.chapters.create(EDITOR, this.nami, Chapter.builder().number(802).releases(twice).build()));
		var episode = catchThrowableOfType(ValueInvalidException.class,
				() -> this.episodes.create(EDITOR, this.nami, Episode.builder().number(802).broadcasts(twice).build()));
		var volume = catchThrowableOfType(ValueInvalidException.class,
				() -> this.volumes.create(EDITOR, this.nami, Volume.builder().number(802).releases(twice).build()));

		assertThat(fieldsOf(chapter)).containsExactly("releases[1].country");
		assertThat(fieldsOf(episode)).containsExactly("broadcasts[1].country");
		assertThat(fieldsOf(volume)).containsExactly("releases[1].country");
	}

	@Test
	void aCountryOutsideTheIsoListIsRefusedAndALowercaseOneIsFine() {
		var refused = catchThrowableOfType(ValueInvalidException.class,
				() -> this.chapters.create(EDITOR, this.nami,
						Chapter.builder()
							.number(803)
							.releases(List.of(new Release("JP", DAY), new Release("ZZ", DAY)))
							.build()));
		var accepted = this.chapters.create(EDITOR, this.nami,
				Chapter.builder().number(803).releases(List.of(new Release("it", DAY))).build());
		this.fixtures.stored();

		assertThat(fieldsOf(refused)).containsExactly("releases[1].country");
		assertThat(this.chapters.getVersion(EDITOR, this.nami, accepted.id(), 1).version().body().releases())
			.containsExactly(new Release("IT", DAY));
	}

	@Test
	void anIncompleteChapterNamesItsNumberItsArcAndItsTextsWhenSubmitted() {
		var draft = this.chapters.create(EDITOR, this.chopper,
				Chapter.builder()
					.pageCount(20)
					.translations(Map.of("en", new StoryTranslation("Chapter", "d")))
					.build());
		this.fixtures.stored();

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.chapters.submit(EDITOR, this.chopper, draft.id(), 1));

		assertThat(fieldsOf(refused)).containsExactly("number", "arc", "translations[it].title",
				"translations[it].description");
	}

	@Test
	void aCompleteChapterWithoutVolumeOrAnyExtraAndACompleteFillerAreSentToReview() {
		var chapter = this.chapters.create(EDITOR, this.chopper,
				StoryFixtures.completeChapter(804, this.romanceDawn, null));
		var filler = this.episodes.create(EDITOR, this.chopper, StoryFixtures.completeEpisode(804, null));
		var volume = this.volumes.create(EDITOR, this.chopper, StoryFixtures.completeVolume(804));
		this.fixtures.stored();

		assertThat(this.chapters.submit(EDITOR, this.chopper, chapter.id(), 1).version().status())
			.isEqualTo(VersionStatus.IN_REVIEW);
		assertThat(this.episodes.submit(EDITOR, this.chopper, filler.id(), 1).version().status())
			.isEqualTo(VersionStatus.IN_REVIEW);
		assertThat(this.volumes.submit(EDITOR, this.chopper, volume.id(), 1).version().status())
			.isEqualTo(VersionStatus.IN_REVIEW);
	}

	@Test
	void aLanguageAStoryItemSaysSomethingInIsInUse() {
		assertThat(this.chapterRepository.existsByLanguage("it")).isTrue();
		assertThat(this.episodeRepository.existsByLanguage("it")).isTrue();
		assertThat(this.volumeRepository.existsByLanguage("it")).isTrue();
		assertThat(this.chapterRepository.existsByLanguage("fr")).isFalse();
	}

	private ArcService arcService() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		return new ArcService(this.arcRepository, this.contentVersionRepository, this.contentRepository,
				new ArcValidator(this.arcRepository, this.languageRepository, this.sagaRepository),
				new ArcRules(this.contentRepository, this.sagaRepository, this.chapterRepository,
						this.episodeRepository, new RulesProperties(5)),
				new AuditLogService(this.auditLogRepository, clock), clock);
	}

}
