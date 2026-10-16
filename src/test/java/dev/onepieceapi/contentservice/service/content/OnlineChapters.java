package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ChapterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Seeds a chapter for the tests of what points to one - the first appearance of a fruit
 * or a type - with a single version in the status asked for, so only what is under test
 * can keep an item from going online.
 */
public final class OnlineChapters {

	private static final Instant EARLIER = Instant.parse("2026-10-10T10:00:00Z");

	private static final User AUTHOR = new User(UUID.randomUUID(), "robin", "robin@onepiece.local");

	private OnlineChapters() {
	}

	/** A chapter online, as the first appearance it can be. */
	public static StoryPoint onlineChapter(ContentRepository contents, ChapterVersionRepository chapters) {
		return new StoryPoint(chapter(contents, chapters, VersionStatus.PUBLISHED));
	}

	/** A chapter with a single version in this status. */
	public static UUID chapter(ContentRepository contents, ChapterVersionRepository chapters, VersionStatus status) {
		return chapter(contents, chapters, status, 1);
	}

	/** A chapter with this number, with a single version in this status. */
	public static UUID chapter(ContentRepository contents, ChapterVersionRepository chapters, VersionStatus status,
			int number) {
		UUID contentId = contents.save(new ContentEntity(UUID.randomUUID(), EntityType.CHAPTER, EARLIER)).getId();
		version(chapters, contentId, 1, status, number);
		return contentId;
	}

	/**
	 * One more version of a chapter, with this version number, status and chapter number.
	 */
	public static void version(ChapterVersionRepository chapters, UUID contentId, int versionNumber,
			VersionStatus status, int number) {
		var version = new ChapterVersionEntity(ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(versionNumber)
			.basedOnNumber(versionNumber == 1 ? null : versionNumber - 1)
			.author(UserMapper.toEmbeddable(AUTHOR))
			.status(status)
			.createdAt(EARLIER)
			.updatedAt(EARLIER)
			.build());
		StoryVersionMapper.rewrite(version, Chapter.builder().number(number).build(), EARLIER);
		chapters.save(version);
	}

}
