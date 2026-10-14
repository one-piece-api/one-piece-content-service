package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * What the chapters and the episodes say of their arcs, and the chapters of their
 * volumes, read against a real PostgreSQL (Testcontainers): the arcs and the volumes they
 * may be linked to, by number and narrowed by what the number or a title contains, and
 * those they point to as they are today (implementation plan of the story structure, S4,
 * S8) - the same as {@link SagaLinksIntegrationTest} for the sagas of the arcs.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class StoryItemsLinksIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

	private final User chopper = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

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
	private TestEntityManager entityManager;

	private StoryFixtures fixtures;

	private ArcLinks arcLinks;

	private VolumeLinks volumeLinks;

	private UUID published;

	private UUID ready;

	private UUID archived;

	private UUID draft;

	private int publishedNumber;

	private int readyNumber;

	private int archivedNumber;

	private int volumeNumber;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		this.arcLinks = new ArcLinks(this.arcRepository, clock);
		this.volumeLinks = new VolumeLinks(this.volumeRepository, clock);
		this.fixtures = new StoryFixtures(this.entityManager, this.sagaRepository, this.arcRepository,
				this.volumeRepository, this.chapterRepository, this.episodeRepository, this.chopper);

		UUID saga = this.fixtures.saga(PUBLISHED);
		this.published = this.fixtures.arc(saga, PUBLISHED);
		this.publishedNumber = this.fixtures.lastNumber();
		this.ready = this.fixtures.arc(saga, READY_TO_PUBLISH);
		this.readyNumber = this.fixtures.lastNumber();
		this.archived = this.fixtures.arc(saga, ARCHIVED);
		this.archivedNumber = this.fixtures.lastNumber();
		this.draft = this.fixtures.arc(saga, DRAFT);
		this.fixtures.arc(saga, IN_REVIEW);
		this.fixtures.volume(PUBLISHED);
		this.volumeNumber = this.fixtures.lastNumber();
		this.fixtures.volume(DRAFT);
		this.fixtures.stored();
	}

	@Test
	void onlyTheArcsWithAnApprovedVersionCanBeLinkedInTheOrderOfTheirNumber() {
		var page = this.arcLinks.linkable(null, 0, 10);

		assertThat(page.getContent()).extracting(NumberedReference::id)
			.containsExactly(this.published, this.ready, this.archived);
		assertThat(page.getContent().getFirst()).isEqualTo(
				new NumberedReference(this.published, this.publishedNumber, Map.of("it", "Arc it", "en", "Arc")));
		assertThat(page.getTotalElements()).isEqualTo(3);
	}

	@Test
	void onlyTheVolumesWithAnApprovedVersionCanBeLinked() {
		var page = this.volumeLinks.linkable(null, 0, 10);

		assertThat(page.getContent()).hasSize(1);
		assertThat(page.getContent().getFirst().names()).containsOnly(entry("it", "Volume it " + this.volumeNumber),
				entry("en", "Volume " + this.volumeNumber));
	}

	@Test
	void theLinkableArcsAreNarrowedByWhatTheirNumberOrATitleContainsAndPaged() {
		assertThat(this.arcLinks.linkable("arc", 0, 10).getContent()).hasSize(3);
		assertThat(this.arcLinks.linkable("zzz", 0, 10).getContent()).isEmpty();
		assertThat(this.arcLinks.linkable(String.valueOf(this.readyNumber), 0, 10).getContent())
			.extracting(NumberedReference::id)
			.containsExactly(this.ready);
		var second = this.arcLinks.linkable(null, 1, 2);
		assertThat(second.getContent()).extracting(NumberedReference::id).containsExactly(this.archived);
		assertThat(second.getTotalPages()).isEqualTo(2);
	}

	@Test
	void theArcAContentPointsToIsAsItWasLastApprovedNotAsItIsWritten() {
		this.fixtures.arcVersion(this.published, 2, DRAFT,
				new Arc(this.archivedNumber + 100, null, StoryFixtures.translations("Arc Draft")));
		this.fixtures.stored();

		Map<UUID, NumberedReference> references = this.arcLinks
			.referencesOf(List.of(this.published, this.archived, this.draft));

		assertThat(references).containsOnlyKeys(this.published, this.archived);
		assertThat(references.get(this.published).number()).isEqualTo(this.publishedNumber);
	}

	@Test
	void theArcsOfSomeEpisodesAndChaptersAreLookedUpOnceAndNoneIsLeftOut() {
		var episodes = List.of(StoryFixtures.completeEpisode(1, this.published),
				StoryFixtures.completeEpisode(2, this.published), StoryFixtures.completeEpisode(3, null),
				StoryFixtures.completeEpisode(4, this.ready));
		var chapters = List.of(StoryFixtures.completeChapter(1, this.archived, null));

		assertThat(this.arcLinks.referencesFor(episodes, Episode::arcContentId)).containsOnlyKeys(this.published,
				this.ready);
		assertThat(this.arcLinks.referencesFor(chapters, Chapter::arcContentId)).containsOnlyKeys(this.archived);
		assertThat(this.arcLinks.referencesOf(List.of())).isEmpty();
	}

	@Test
	void aChapterWithoutAVolumeHasNoVolumeToLookUp() {
		var chapters = List.of(StoryFixtures.completeChapter(1, this.published, null),
				StoryFixtures.completeChapter(2, this.published, null));

		assertThat(this.volumeLinks.referencesFor(chapters, Chapter::volumeContentId)).isEmpty();
	}

	@Test
	void theVolumeOfAChapterIsLookedUpWithItsNumberAndTitles() {
		UUID volume = this.fixtures.volume(VersionStatus.PUBLISHED);
		int number = this.fixtures.lastNumber();
		this.fixtures.stored();

		var references = this.volumeLinks
			.referencesFor(List.of(StoryFixtures.completeChapter(1, this.published, volume)), Chapter::volumeContentId);

		assertThat(references.get(volume)).isEqualTo(
				new NumberedReference(volume, number, Map.of("it", "Volume it " + number, "en", "Volume " + number)));
	}

}
