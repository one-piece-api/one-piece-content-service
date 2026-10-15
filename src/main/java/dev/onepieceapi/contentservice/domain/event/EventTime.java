package dev.onepieceapi.contentservice.domain.event;

import java.util.UUID;

/**
 * When an event happens, or ends (docs/user-flows/content-editorial-workflow.md 3.1.8;
 * implementation plan of the timeline, T3): a tagged union, either a chapter - everything
 * from chapter 1 on, on-page or off-page - or a date of the world calendar, before the
 * story starts. Every world date comes before every chapter, so any two are ordered
 * without turning chapters into years.
 */
public sealed interface EventTime permits EventTime.ChapterTime, EventTime.WorldDateTime {

	/** The chapter it is, if it is one; null for a world date. */
	default UUID chapterContentId() {
		return this instanceof ChapterTime(UUID chapter) ? chapter : null;
	}

	/**
	 * In the story: a chapter.
	 *
	 * @param chapter the content of the chapter
	 */
	record ChapterTime(UUID chapter) implements EventTime {

	}

	/** Before the story: a year, or a span of years, of the world calendar. */
	record WorldDateTime(WorldDate date) implements EventTime {

	}

}
