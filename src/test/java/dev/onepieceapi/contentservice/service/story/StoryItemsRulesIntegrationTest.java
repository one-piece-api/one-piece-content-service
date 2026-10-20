package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.BlockedAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
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
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.RETIRED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * What chapters, episodes, volumes and arcs owe each other (implementation plan of the
 * story structure, S5) against a real PostgreSQL (Testcontainers): a chapter goes online
 * only while its arc is, and its volume when it has one; an episode only while its arc
 * is, when it has one; an arc is not retired while chapters or episodes are online in it,
 * nor a volume while chapters are. A relation left out needs and blocks nothing. Whoever
 * tries anyway is refused with the reason, even an administrator, and the reason is
 * listed with the version before anyone tries. The two sides running at the same instant
 * are in {@link StoryItemsRulesRaceIntegrationTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class StoryItemsRulesIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	private static final Set<Permission> RETIRER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_RETIRE);

	private static final Set<Permission> ADMIN = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE,
			Permission.CONTENT_REVIEW, Permission.CONTENT_PUBLISH, Permission.CONTENT_RETIRE, Permission.CONTENT_ADMIN);

	private final User vivi = new User(UUID.randomUUID(), "vivi", "vivi@onepiece.local");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Autowired
	private DevilFruitTypeVersionRepository typeRepository;

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

	private ArcService arcs;

	private VolumeService volumes;

	private ChapterService chapters;

	private EpisodeService episodes;

	private UUID saga;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var auditLogService = new AuditLogService(this.auditLogRepository, clock);
		var properties = new RulesProperties(5);
		var images = new ContentImages(new ImageValidator(), new ImageNormalizer(new PngImageEncoder()),
				new JpaImageStore(this.imageRepository, clock), StoryFixtures.VOLUME_IMAGES);
		this.arcs = new ArcService(this.arcRepository, this.contentVersionRepository, this.contentRepository,
				new ArcValidator(this.arcRepository, this.languageRepository, this.sagaRepository),
				new ArcRules(this.contentRepository, this.sagaRepository, this.chapterRepository,
						this.episodeRepository, properties),
				auditLogService, clock);
		this.volumes = new VolumeService(this.contentVersionRepository, this.contentRepository, auditLogService, clock,
				images,
				new VolumeDefinition(this.volumeRepository,
						new VolumeValidator(this.volumeRepository, this.languageRepository),
						new VolumeRules(this.contentRepository, this.chapterRepository, properties)));
		this.chapters = new ChapterService(this.chapterRepository, this.contentVersionRepository,
				this.contentRepository,
				new ChapterValidator(this.chapterRepository, this.languageRepository, this.arcRepository,
						this.volumeRepository),
				new ChapterRules(this.contentRepository, this.arcRepository, this.volumeRepository,
						this.fruitRepository, this.typeRepository, new RulesProperties(5)),
				auditLogService, clock);
		this.episodes = new EpisodeService(this.episodeRepository, this.contentVersionRepository,
				this.contentRepository,
				new EpisodeValidator(this.episodeRepository, this.languageRepository, this.arcRepository),
				new EpisodeRules(this.contentRepository, this.arcRepository, this.fruitRepository, this.typeRepository,
						new RulesProperties(5)),
				auditLogService, clock);

		this.fixtures = new StoryFixtures(this.entityManager, this.sagaRepository, this.arcRepository,
				this.volumeRepository, this.chapterRepository, this.episodeRepository, this.chopper);
		this.saga = this.fixtures.saga(PUBLISHED);
	}

	@Test
	void aChapterGoesOnlineWhileItsArcAndItsVolumeAre() {
		UUID chapter = this.fixtures.chapter(this.fixtures.arc(this.saga, PUBLISHED), this.fixtures.volume(PUBLISHED),
				READY_TO_PUBLISH);
		this.fixtures.stored();

		assertThat(this.chapters.publish(PUBLISHER, this.vivi, chapter, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void aChapterWithoutAVolumeGoesOnlineWhileItsArcIs() {
		UUID chapter = this.fixtures.chapter(this.fixtures.arc(this.saga, PUBLISHED), null, READY_TO_PUBLISH);
		this.fixtures.stored();

		assertThat(this.chapters.publish(PUBLISHER, this.vivi, chapter, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@ParameterizedTest
	@EnumSource(names = { "READY_TO_PUBLISH", "ARCHIVED", "RETIRED" })
	void aChapterDoesNotGoOnlineWhileItsArcIsNotAndTheRefusalNamesTheArc(VersionStatus arcStatus) {
		UUID arc = this.fixtures.arc(this.saga, arcStatus);
		int arcNumber = this.fixtures.lastNumber();
		UUID chapter = this.fixtures.chapter(arc, this.fixtures.volume(PUBLISHED), READY_TO_PUBLISH);
		this.fixtures.stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.chapters.publish(PUBLISHER, this.vivi, chapter, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail")).isEqualTo(Map.of("arcId", arc, "arcNumber", arcNumber));
		this.fixtures.stored();
		assertThat(statusOfChapter(chapter)).isEqualTo(READY_TO_PUBLISH);
	}

	@ParameterizedTest
	@EnumSource(names = { "READY_TO_PUBLISH", "ARCHIVED", "RETIRED" })
	void aChapterDoesNotGoOnlineWhileItsVolumeIsNotAndTheRefusalNamesTheVolume(VersionStatus volumeStatus) {
		UUID volume = this.fixtures.volume(volumeStatus);
		int volumeNumber = this.fixtures.lastNumber();
		UUID chapter = this.fixtures.chapter(this.fixtures.arc(this.saga, PUBLISHED), volume, READY_TO_PUBLISH);
		this.fixtures.stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.chapters.publish(PUBLISHER, this.vivi, chapter, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail"))
			.isEqualTo(Map.of("volumeId", volume, "volumeNumber", volumeNumber));
	}

	@Test
	void aChapterTakenOfflineIsRestoredWhileItsArcAndItsVolumeAreOnlineAndNotBefore() {
		UUID offline = this.fixtures.arc(this.saga, RETIRED);
		UUID online = this.fixtures.arc(this.saga, PUBLISHED);
		UUID early = this.fixtures.chapter(offline, null, RETIRED);
		UUID fine = this.fixtures.chapter(online, this.fixtures.volume(PUBLISHED), RETIRED);
		this.fixtures.stored();

		assertThatThrownBy(() -> this.chapters.restore(PUBLISHER, this.vivi, early, 1))
			.isInstanceOf(VersionActionBlockedException.class);
		assertThat(this.chapters.restore(PUBLISHER, this.vivi, fine, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void anEpisodeWithoutAnArcGoesOnlineWhateverTheArcsAre() {
		this.fixtures.arc(this.saga, RETIRED);
		UUID filler = this.fixtures.episode(null, READY_TO_PUBLISH);
		this.fixtures.stored();

		assertThat(this.episodes.publish(PUBLISHER, this.vivi, filler, 1).version().status()).isEqualTo(PUBLISHED);
	}

	@Test
	void anEpisodeGoesOnlineWhileItsArcIsAndNotWhenItIsNot() {
		UUID online = this.fixtures.arc(this.saga, PUBLISHED);
		UUID offline = this.fixtures.arc(this.saga, ARCHIVED);
		int offlineNumber = this.fixtures.lastNumber();
		UUID fine = this.fixtures.episode(online, READY_TO_PUBLISH);
		UUID early = this.fixtures.episode(offline, READY_TO_PUBLISH);
		this.fixtures.stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.episodes.publish(PUBLISHER, this.vivi, early, 1));

		assertThat(this.episodes.publish(PUBLISHER, this.vivi, fine, 1).version().status()).isEqualTo(PUBLISHED);
		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.RELATED_NOT_ONLINE);
		assertThat(refused.getDetails().get("detail")).isEqualTo(Map.of("arcId", offline, "arcNumber", offlineNumber));
	}

	@Test
	void theVersionSaysBeforeAnyoneTriesWhichOfItsActionsAreBlocked() {
		UUID offline = this.fixtures.arc(this.saga, RETIRED);
		int offlineNumber = this.fixtures.lastNumber();
		UUID blocked = this.fixtures.chapter(offline, null, READY_TO_PUBLISH);
		UUID free = this.fixtures.chapter(this.fixtures.arc(this.saga, PUBLISHED), null, READY_TO_PUBLISH);
		this.fixtures.stored();

		var withReason = this.chapters.getVersion(PUBLISHER, this.vivi, blocked, 1);
		var without = this.chapters.getVersion(PUBLISHER, this.vivi, free, 1);

		assertThat(withReason.allowedActions()).contains(VersionAction.PUBLISH);
		assertThat(withReason.blockedActions()).containsExactly(new BlockedAction(VersionAction.PUBLISH,
				BlockReason.RELATED_NOT_ONLINE, Map.of("arcId", offline, "arcNumber", offlineNumber)));
		assertThat(without.blockedActions()).isEmpty();
	}

	@Test
	void anArcIsNotRetiredWhileChaptersAreOnlineInItAndTheRefusalNamesSome() {
		UUID arc = this.fixtures.arc(this.saga, PUBLISHED);
		List<UUID> chapterIds = IntStream.range(0, 6)
			.mapToObj(index -> this.fixtures.chapter(arc, null, PUBLISHED))
			.toList();
		this.fixtures.stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.arcs.retire(RETIRER, this.vivi, arc, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.ONLINE_ITEMS_LINKED);
		@SuppressWarnings("unchecked")
		var detail = (Map<String, Object>) refused.getDetails().get("detail");
		assertThat(detail).containsEntry("count", 6L);
		@SuppressWarnings("unchecked")
		var named = (List<Map<String, Object>>) detail.get("chapters");
		assertThat(named).extracting(chapter -> chapter.get("id")).containsExactlyElementsOf(chapterIds.subList(0, 5));
		assertThat(named.getFirst()).containsOnlyKeys("id", "number");
		this.fixtures.stored();
		assertThat(statusOfArc(arc)).isEqualTo(PUBLISHED);
	}

	@Test
	void anArcIsNotRetiredWhileEpisodesAreOnlineInItAndTheVersionListsIt() {
		UUID arc = this.fixtures.arc(this.saga, PUBLISHED);
		UUID episode = this.fixtures.episode(arc, PUBLISHED);
		int episodeNumber = this.fixtures.lastNumber();
		UUID alone = this.fixtures.arc(this.saga, PUBLISHED);
		this.fixtures.stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.arcs.retire(RETIRER, this.vivi, arc, 1));
		var withEpisode = this.arcs.getVersion(RETIRER, this.vivi, arc, 1);
		var withoutAnything = this.arcs.getVersion(RETIRER, this.vivi, alone, 1);

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.ONLINE_ITEMS_LINKED);
		@SuppressWarnings("unchecked")
		var detail = (Map<String, Object>) refused.getDetails().get("detail");
		assertThat(detail).containsEntry("count", 1L);
		assertThat(detail.get("episodes")).isEqualTo(List.of(Map.of("id", episode, "number", episodeNumber)));
		assertThat(withEpisode.blockedActions()).extracting(BlockedAction::action, BlockedAction::reason)
			.containsExactly(tuple(VersionAction.RETIRE, BlockReason.ONLINE_ITEMS_LINKED));
		assertThat(withoutAnything.blockedActions()).isEmpty();
	}

	@Test
	void contentsNotOnlineInTheArcDoNotKeepItOnline() {
		UUID arc = this.fixtures.arc(this.saga, PUBLISHED);
		UUID other = this.fixtures.arc(this.saga, PUBLISHED);
		for (VersionStatus status : List.of(DRAFT, READY_TO_PUBLISH, ARCHIVED, RETIRED, SUPERSEDED)) {
			this.fixtures.chapter(arc, null, status);
			this.fixtures.episode(arc, status);
		}
		this.fixtures.chapter(other, null, PUBLISHED);
		this.fixtures.episode(other, PUBLISHED);
		this.fixtures.stored();

		assertThat(this.arcs.retire(RETIRER, this.vivi, arc, 1).version().status()).isEqualTo(RETIRED);
	}

	@Test
	void anEpisodeOutsideAnyArcKeepsNoArcOnline() {
		UUID arc = this.fixtures.arc(this.saga, PUBLISHED);
		this.fixtures.episode(null, PUBLISHED);
		this.fixtures.stored();

		assertThat(this.arcs.retire(RETIRER, this.vivi, arc, 1).version().status()).isEqualTo(RETIRED);
	}

	@Test
	void aVolumeIsNotRetiredWhileChaptersAreOnlineInIt() {
		UUID volume = this.fixtures.volume(PUBLISHED);
		UUID chapter = this.fixtures.chapter(this.fixtures.arc(this.saga, PUBLISHED), volume, PUBLISHED);
		int chapterNumber = this.fixtures.lastNumber();
		this.fixtures.stored();

		var refused = catchThrowableOfType(VersionActionBlockedException.class,
				() -> this.volumes.retire(RETIRER, this.vivi, volume, 1));

		assertThat(refused.getDetails()).containsEntry("reason", BlockReason.ONLINE_ITEMS_LINKED);
		@SuppressWarnings("unchecked")
		var detail = (Map<String, Object>) refused.getDetails().get("detail");
		assertThat(detail).containsEntry("count", 1L);
		assertThat(detail.get("chapters")).isEqualTo(List.of(Map.of("id", chapter, "number", chapterNumber)));
		assertThat(this.volumes.getVersion(RETIRER, this.vivi, volume, 1).blockedActions())
			.extracting(BlockedAction::action, BlockedAction::reason)
			.containsExactly(tuple(VersionAction.RETIRE, BlockReason.ONLINE_ITEMS_LINKED));
	}

	@Test
	void chaptersNotOnlineInTheVolumeOrOnlineInNoneDoNotKeepItOnline() {
		UUID volume = this.fixtures.volume(PUBLISHED);
		UUID arc = this.fixtures.arc(this.saga, PUBLISHED);
		for (VersionStatus status : List.of(DRAFT, READY_TO_PUBLISH, ARCHIVED, RETIRED, SUPERSEDED)) {
			this.fixtures.chapter(arc, volume, status);
		}
		this.fixtures.chapter(arc, null, PUBLISHED);
		this.fixtures.stored();

		assertThat(this.volumes.retire(RETIRER, this.vivi, volume, 1).version().status()).isEqualTo(RETIRED);
	}

	@Test
	void nothingDependsOnAChapterOrAnEpisodeSoTheyAreRetiredFreely() {
		UUID arc = this.fixtures.arc(this.saga, PUBLISHED);
		UUID chapter = this.fixtures.chapter(arc, this.fixtures.volume(PUBLISHED), PUBLISHED);
		UUID episode = this.fixtures.episode(arc, PUBLISHED);
		this.fixtures.stored();

		assertThat(this.chapters.retire(RETIRER, this.vivi, chapter, 1).version().status()).isEqualTo(RETIRED);
		assertThat(this.episodes.retire(RETIRER, this.vivi, episode, 1).version().status()).isEqualTo(RETIRED);
	}

	@Test
	void anAdministratorIsRefusedToo() {
		UUID offline = this.fixtures.arc(this.saga, RETIRED);
		UUID early = this.fixtures.chapter(offline, null, READY_TO_PUBLISH);
		UUID earlyEpisode = this.fixtures.episode(offline, READY_TO_PUBLISH);
		UUID arc = this.fixtures.arc(this.saga, PUBLISHED);
		UUID volume = this.fixtures.volume(PUBLISHED);
		this.fixtures.chapter(arc, volume, PUBLISHED);
		this.fixtures.episode(arc, PUBLISHED);
		this.fixtures.stored();

		assertThatThrownBy(() -> this.chapters.publish(ADMIN, this.vivi, early, 1))
			.isInstanceOf(VersionActionBlockedException.class);
		assertThatThrownBy(() -> this.episodes.publish(ADMIN, this.vivi, earlyEpisode, 1))
			.isInstanceOf(VersionActionBlockedException.class);
		assertThatThrownBy(() -> this.arcs.retire(ADMIN, this.vivi, arc, 1))
			.isInstanceOf(VersionActionBlockedException.class);
		assertThatThrownBy(() -> this.volumes.retire(ADMIN, this.vivi, volume, 1))
			.isInstanceOf(VersionActionBlockedException.class);
	}

	@Test
	void aNewOnlineVersionOfTheArcChangesNothingForItsChapters() {
		UUID arc = this.fixtures.arc(this.saga, PUBLISHED);
		this.fixtures.arcVersion(arc, 2, READY_TO_PUBLISH,
				new Arc(this.fixtures.lastNumber() + 100, this.saga, StoryFixtures.translations("Arc")));
		UUID chapter = this.fixtures.chapter(arc, null, PUBLISHED);
		this.fixtures.stored();

		this.arcs.publish(PUBLISHER, this.vivi, arc, 2);
		this.fixtures.stored();

		assertThat(statusOfChapter(chapter)).isEqualTo(PUBLISHED);
		assertThatThrownBy(() -> this.arcs.retire(RETIRER, this.vivi, arc, 2))
			.isInstanceOf(VersionActionBlockedException.class);
	}

	private VersionStatus statusOfChapter(UUID contentId) {
		return this.chapters.getVersion(ADMIN, this.vivi, contentId, 1).version().status();
	}

	private VersionStatus statusOfArc(UUID contentId) {
		return this.arcs.getVersion(ADMIN, this.vivi, contentId, 1).version().status();
	}

}
