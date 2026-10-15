package dev.onepieceapi.contentservice.domain.readertime;

import java.util.UUID;

/**
 * The capability of what the reader meets at some point of the story
 * (docs/user-flows/content-editorial-workflow.md 3.6; implementation plan of the
 * timeline, T7): a Devil Fruit, a Devil Fruit Type, a subcategory of a type. Never part
 * of completeness: whether it is required to go online is the entity's rule to say - the
 * fruit's and the type's are, a subcategory's never.
 */
public interface FirstAppearing {

	/**
	 * Where the reader first meets it, in any form (power, name or object);
	 * {@link StoryPoint#NONE} for none.
	 */
	StoryPoint firstAppearance();

	/** The chapter of {@link #firstAppearance()}; null for none. */
	default UUID firstAppearanceChapterId() {
		return firstAppearance().chapterContentId();
	}

	/** Whether no part of {@link #firstAppearance()} is chosen, in any medium. */
	default boolean lacksFirstAppearance() {
		return firstAppearance().isMissing();
	}

}
