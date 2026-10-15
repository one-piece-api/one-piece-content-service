package dev.onepieceapi.contentservice.domain.readertime;

import java.util.UUID;

/**
 * A point of the reader axis (docs/user-flows/content-editorial-workflow.md 3.6;
 * implementation plan of the timeline, T7): where the reader is in the story, one part
 * per medium. Only the manga part, a chapter, exists today; the anime part, an episode,
 * is an open door - one more component, never a change of shape (ADR-0008).
 *
 * @param chapterContentId the content of the chapter; null for none
 */
public record StoryPoint(UUID chapterContentId) {

	/** No point at all: nothing chosen yet. */
	public static final StoryPoint NONE = new StoryPoint(null);

	/** The point given, or {@link #NONE} for none. */
	public static StoryPoint orNone(StoryPoint point) {
		return point == null ? NONE : point;
	}

	/** Whether no part of it is chosen, in any medium. */
	public boolean isMissing() {
		return this.chapterContentId == null;
	}

}
