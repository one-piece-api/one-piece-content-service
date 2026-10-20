package dev.onepieceapi.contentservice.domain.readertime;

import java.util.UUID;

/**
 * The capability of what the reader meets at some point of the story
 * (docs/user-flows/content-editorial-workflow.md 3.6; ADR-0008): a Devil Fruit, a Devil
 * Fruit Type, a subcategory of a type. Whether it is required is the entity's rule to
 * say: the fruit and the type ask for it to be submitted (completeness, owner 2026-10-16)
 * and to go online - in at least one medium; a subcategory never.
 */
public interface FirstAppearing {

	/** The first appearance as a completeness check names it: no part chosen. */
	String FIELD = "firstAppearance";

	/** Its chapter, as a request names it. */
	String CHAPTER_FIELD = FIELD + ".chapter";

	/** Its episode, as a request names it. */
	String EPISODE_FIELD = FIELD + ".episode";

	/**
	 * Where the reader first meets it, in any form (power, name or object);
	 * {@link StoryPoint#NONE} for none.
	 */
	StoryPoint firstAppearance();

	/** The chapter of {@link #firstAppearance()}; null for none. */
	default UUID firstAppearanceChapterId() {
		return firstAppearance().chapterContentId();
	}

	/** The episode of {@link #firstAppearance()}; null for none. */
	default UUID firstAppearanceEpisodeId() {
		return firstAppearance().episodeContentId();
	}

	/** Whether no part of {@link #firstAppearance()} is chosen, in any medium. */
	default boolean lacksFirstAppearance() {
		return firstAppearance().isMissing();
	}

}
