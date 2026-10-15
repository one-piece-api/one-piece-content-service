package dev.onepieceapi.contentservice.service.devilfruittype;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategory;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.RelationDescriptor;

/**
 * The relations of a Devil Fruit Type (implementation plan of the timeline, T7): the
 * chapter it first appears in, and the one each of its subcategories does. Both keep a
 * chapter from being retired as {@code types}: a type online is named whichever of the
 * two points to it.
 */
public final class DevilFruitTypeRelations {

	/** The chapter a type first appears in. */
	public static final RelationDescriptor<DevilFruitType> FIRST_APPEARANCE = FirstAppearanceRules.relation("types");

	/** The chapter a subcategory of a type first appears in, always optional. */
	public static final RelationDescriptor<DevilFruitTypeSubcategory> SUBCATEGORY_FIRST_APPEARANCE = FirstAppearanceRules
		.relation("types");

	private DevilFruitTypeRelations() {
	}

}
