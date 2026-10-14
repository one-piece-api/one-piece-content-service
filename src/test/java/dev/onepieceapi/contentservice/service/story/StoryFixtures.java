package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.config.ImageProperties;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.story.Volume;
import dev.onepieceapi.contentservice.domain.story.VolumeTranslation;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ArcVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ChapterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.EpisodeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.SagaVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.VolumeVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.exception.web.FieldViolation;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seeds the story entities for the tests of their rules, each with a single version in
 * the status asked for, complete in both languages of the catalog - so only what is under
 * test can be missing. Every content gets a number of its own (per entity), and its title
 * says it. What is seeded is written and read again by the test, with {@link #stored()}.
 */
final class StoryFixtures {

	static final Instant EARLIER = Instant.parse("2026-10-13T10:00:00Z").minus(Duration.ofDays(3));

	/** The volume profile as configured. */
	static final ImageProperties VOLUME_IMAGES = new ImageProperties("/api/content", Map.of(EntityType.VOLUME,
			new ImageProperties.Profile(420, 600, 560, 800, 0.1, DataSize.ofMegabytes(5), 25_000_000, 0)));

	private final TestEntityManager entityManager;

	private final SagaVersionRepository sagas;

	private final ArcVersionRepository arcs;

	private final VolumeVersionRepository volumes;

	private final ChapterVersionRepository chapters;

	private final EpisodeVersionRepository episodes;

	private final User author;

	private int numbers;

	StoryFixtures(TestEntityManager entityManager, SagaVersionRepository sagas, ArcVersionRepository arcs,
			VolumeVersionRepository volumes, ChapterVersionRepository chapters, EpisodeVersionRepository episodes,
			User author) {
		this.entityManager = entityManager;
		this.sagas = sagas;
		this.arcs = arcs;
		this.volumes = volumes;
		this.chapters = chapters;
		this.episodes = episodes;
		this.author = author;
	}

	/** What was done so far is written and read again from the database. */
	void stored() {
		this.entityManager.flush();
		this.entityManager.clear();
	}

	/** The number the last content seeded was given. */
	int lastNumber() {
		return this.numbers;
	}

	UUID saga(VersionStatus status) {
		UUID contentId = content(EntityType.SAGA);
		var version = new SagaVersionEntity(workflow(contentId, 1, status));
		StoryVersionMapper.rewrite(version, new Saga(++this.numbers, translations("Saga")), EARLIER);
		this.sagas.save(version);
		return contentId;
	}

	UUID arc(UUID saga, VersionStatus status) {
		UUID contentId = content(EntityType.ARC);
		arcVersion(contentId, 1, status, new Arc(++this.numbers, saga, translations("Arc")));
		return contentId;
	}

	void arcVersion(UUID contentId, int number, VersionStatus status, Arc body) {
		var version = new ArcVersionEntity(workflow(contentId, number, status));
		StoryVersionMapper.rewrite(version, body, EARLIER);
		this.arcs.save(version);
	}

	UUID volume(VersionStatus status) {
		UUID contentId = content(EntityType.VOLUME);
		volumeVersion(contentId, 1, status, completeVolume(++this.numbers));
		return contentId;
	}

	void volumeVersion(UUID contentId, int number, VersionStatus status, Volume body) {
		var version = new VolumeVersionEntity(workflow(contentId, number, status));
		StoryVersionMapper.rewrite(version, body, EARLIER);
		this.volumes.save(version);
	}

	/** A chapter of this arc, in this volume if any. */
	UUID chapter(UUID arc, UUID volume, VersionStatus status) {
		UUID contentId = content(EntityType.CHAPTER);
		chapterVersion(contentId, 1, status, completeChapter(++this.numbers, arc, volume));
		return contentId;
	}

	void chapterVersion(UUID contentId, int number, VersionStatus status, Chapter body) {
		var version = new ChapterVersionEntity(workflow(contentId, number, status));
		StoryVersionMapper.rewrite(version, body, EARLIER);
		this.chapters.save(version);
	}

	/** An episode of this arc, or of none. */
	UUID episode(UUID arc, VersionStatus status) {
		UUID contentId = content(EntityType.EPISODE);
		episodeVersion(contentId, 1, status, completeEpisode(++this.numbers, arc));
		return contentId;
	}

	void episodeVersion(UUID contentId, int number, VersionStatus status, Episode body) {
		var version = new EpisodeVersionEntity(workflow(contentId, number, status));
		StoryVersionMapper.rewrite(version, body, EARLIER);
		this.episodes.save(version);
	}

	static Chapter completeChapter(int number, UUID arc, UUID volume) {
		return Chapter.builder()
			.number(number)
			.arcContentId(arc)
			.volumeContentId(volume)
			.translations(translations("Chapter"))
			.build();
	}

	static Episode completeEpisode(int number, UUID arc) {
		return Episode.builder().number(number).arcContentId(arc).translations(translations("Episode")).build();
	}

	static Volume completeVolume(int number) {
		return Volume.builder()
			.number(number)
			.translations(Map.of("it", new VolumeTranslation("Volume it " + number), "en",
					new VolumeTranslation("Volume " + number)))
			.build();
	}

	/** Complete in both languages of the catalog. */
	static Map<String, StoryTranslation> translations(String title) {
		return Map.of("it", new StoryTranslation(title + " it", "Descrizione"), "en",
				new StoryTranslation(title, "Description"));
	}

	/** The fields a refusal names, in order. */
	static List<String> fieldsOf(DomainException refused) {
		@SuppressWarnings("unchecked")
		List<FieldViolation> violations = (List<FieldViolation>) refused.getDetails().get("errors");
		return violations.stream().map(FieldViolation::field).toList();
	}

	private UUID content(EntityType entityType) {
		return this.entityManager.persist(new ContentEntity(UUID.randomUUID(), entityType, EARLIER)).getId();
	}

	private ContentVersionEntity workflow(UUID contentId, int number, VersionStatus status) {
		return ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(number)
			.basedOnNumber(number == 1 ? null : number - 1)
			.author(UserMapper.toEmbeddable(this.author))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build();
	}

}
