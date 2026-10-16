package dev.onepieceapi.contentservice.domain.readertime;

import java.util.UUID;

/**
 * The capability of what the reader meets at some point of the story
 * (docs/user-flows/content-editorial-workflow.md 3.6; implementation plan of the
 * timeline, T7): a Devil Fruit, a Devil Fruit Type, a subcategory of a type. Whether it
 * is required is the entity's rule to say: the fruit and the type ask for it to be
 * submitted (completeness, owner 2026-10-16) and to go online; a subcategory never.
 */
public interface FirstAppearing {

	/** The first appearance as a request and a completeness check name it. */
	String FIELD = "firstAppearance.chapter";

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
