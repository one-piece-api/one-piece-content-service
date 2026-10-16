package dev.onepieceapi.contentservice.domain.readertime;

import java.util.UUID;

/**
 * One reveal of an item (docs/user-flows/content-editorial-workflow.md 3.6;
 * implementation plan of the timeline, T9, T12): an instant of the story where the reader
 * learns something - the fields it names as learned there (T13). Not an event: the world
 * does not change, only the reader's knowledge. Which fields a reveal may hold is the
 * entity's to say.
 */
public interface Reveal {

	/** Where the reader learns; {@link StoryPoint#NONE} while not chosen. */
	StoryPoint revealedAt();

	/** Whether it names no field at all. */
	boolean definesNothing();

	/** The chapter of {@link #revealedAt()}; null for none. */
	default UUID revealedAtChapterId() {
		return revealedAt().chapterContentId();
	}

}
