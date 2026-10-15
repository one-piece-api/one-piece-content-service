package dev.onepieceapi.contentservice.domain.readertime;

import java.util.UUID;

/**
 * One knowledge phase of an item (docs/user-flows/content-editorial-workflow.md 3.6;
 * implementation plan of the timeline, T9): what the reader believed until a point of the
 * story supersedes it - only the fields revealed there, each with the value believed
 * before. Not an event: the world does not change, only the reader's knowledge. Which
 * fields a phase may hold is the entity's to say.
 */
public interface KnowledgePhase {

	/** Where the reader learns better; {@link StoryPoint#NONE} while not chosen. */
	StoryPoint supersededAt();

	/** Whether it holds no field at all. */
	boolean definesNothing();

	/** The chapter of {@link #supersededAt()}; null for none. */
	default UUID supersededAtChapterId() {
		return supersededAt().chapterContentId();
	}

}
