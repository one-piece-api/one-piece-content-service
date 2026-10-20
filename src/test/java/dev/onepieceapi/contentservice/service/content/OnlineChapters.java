package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ChapterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.EpisodeVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Seeds a chapter or an episode for the tests of what points to one - the first
 * appearance of a fruit or a type - with a single version in the status asked for, so
 * only what is under test can keep an item from going online.
 */
public final class OnlineChapters {

	private static final Instant EARLIER = Instant.parse("2026-10-10T10:00:00Z");

	private static final User AUTHOR = new User(UUID.randomUUID(), "robin", "robin@onepiece.local");

	private OnlineChapters() {
	}

	/** A chapter online, as the first appearance it can be. */
	public static StoryPoint onlineChapter(ContentRepository contents, ChapterVersionRepository chapters) {
		return StoryPoint.ofChapter(chapter(contents, chapters, VersionStatus.PUBLISHED));
	}

	/** An episode online, as the first appearance it can be. */
	public static StoryPoint onlineEpisode(ContentRepository contents, EpisodeVersionRepository episodes) {
		return StoryPoint.ofEpisode(episode(contents, episodes, VersionStatus.PUBLISHED));
	}

	/** An episode, number 4, with a single version in this status. */
	public static UUID episode(ContentRepository contents, EpisodeVersionRepository episodes, VersionStatus status) {
		UUID contentId = contents.save(new ContentEntity(UUID.randomUUID(), EntityType.EPISODE, EARLIER)).getId();
		var version = new EpisodeVersionEntity(ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(1)
			.author(UserMapper.toEmbeddable(AUTHOR))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build());
		StoryVersionMapper.rewrite(version, Episode.builder().number(4).build(), EARLIER);
		episodes.save(version);
		return contentId;
	}

	/** A chapter with a single version in this status. */
	public static UUID chapter(ContentRepository contents, ChapterVersionRepository chapters, VersionStatus status) {
		UUID contentId = contents.save(new ContentEntity(UUID.randomUUID(), EntityType.CHAPTER, EARLIER)).getId();
		var version = new ChapterVersionEntity(ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(1)
			.author(UserMapper.toEmbeddable(AUTHOR))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build());
		StoryVersionMapper.rewrite(version, Chapter.builder().number(1).build(), EARLIER);
		chapters.save(version);
		return contentId;
	}

}
