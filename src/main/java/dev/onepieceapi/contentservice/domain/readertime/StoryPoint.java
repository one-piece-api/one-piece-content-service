package dev.onepieceapi.contentservice.domain.readertime;

import java.util.UUID;

/**
 * A point of the story as the reader meets it
 * (docs/user-flows/content-editorial-workflow.md 3.6; ADR-0008), one part per medium: a
 * manga chapter and an anime episode, each optional and independent of the other.
 *
 * @param chapterContentId the content of the chapter; null for none
 * @param episodeContentId the content of the episode; null for none
 */
public record StoryPoint(UUID chapterContentId, UUID episodeContentId) {

	/** No point at all: nothing chosen yet. */
	public static final StoryPoint NONE = new StoryPoint(null, null);

	/** A point in a manga chapter only. */
	public static StoryPoint ofChapter(UUID chapterContentId) {
		return new StoryPoint(chapterContentId, null);
	}

	/** A point in an anime episode only. */
	public static StoryPoint ofEpisode(UUID episodeContentId) {
		return new StoryPoint(null, episodeContentId);
	}

	/** The point given, or {@link #NONE} for none. */
	public static StoryPoint orNone(StoryPoint point) {
		return point == null ? NONE : point;
	}

	/** Whether no part of it is chosen, in any medium. */
	public boolean isMissing() {
		return this.chapterContentId == null && this.episodeContentId == null;
	}

}
