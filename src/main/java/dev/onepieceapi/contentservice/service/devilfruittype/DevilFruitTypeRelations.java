package dev.onepieceapi.contentservice.service.devilfruittype;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategory;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.RelationDescriptor;

/**
 * The relations of a Devil Fruit Type (ADR-0008): the chapter and the episode it first
 * appears in, and those each of its subcategories does. Both keep a chapter or an episode
 * from being retired as {@code types}: a type online is named whichever of the two points
 * to it.
 */
public final class DevilFruitTypeRelations {

	/** The chapter a type first appears in. */
	public static final RelationDescriptor<DevilFruitType> FIRST_APPEARANCE_CHAPTER = FirstAppearanceRules
		.chapterRelation("types");

	/** The episode a type first appears in. */
	public static final RelationDescriptor<DevilFruitType> FIRST_APPEARANCE_EPISODE = FirstAppearanceRules
		.episodeRelation("types");

	/** The chapter a subcategory of a type first appears in, always optional. */
	public static final RelationDescriptor<DevilFruitTypeSubcategory> SUBCATEGORY_CHAPTER = FirstAppearanceRules
		.chapterRelation("types");

	/** The episode a subcategory of a type first appears in, always optional. */
	public static final RelationDescriptor<DevilFruitTypeSubcategory> SUBCATEGORY_EPISODE = FirstAppearanceRules
		.episodeRelation("types");

	private DevilFruitTypeRelations() {
	}

}
