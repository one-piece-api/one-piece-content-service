package dev.onepieceapi.contentservice.domain.devilfruittype;

import dev.onepieceapi.contentservice.domain.workflow.Text;

/**
 * The localized fields of a subcategory of a Devil Fruit Type in one language
 * (docs/user-flows/content-editorial-workflow.md 3.1.1). Either may still be missing
 * while the version is a draft.
 */
public record DevilFruitTypeSubcategoryTranslation(String name, String description) {

	public static final int NAME_MAX_LENGTH = 100;

	public static final int DESCRIPTION_MAX_LENGTH = 2000;

	/** A language nothing was written in. */
	static final DevilFruitTypeSubcategoryTranslation NONE = new DevilFruitTypeSubcategoryTranslation(null, null);

	DevilFruitTypeSubcategoryTranslation normalized() {
		return new DevilFruitTypeSubcategoryTranslation(Text.stripToNull(this.name),
				Text.stripToNull(this.description));
	}

	/** Nothing written in this language. */
	boolean isEmpty() {
		return this.name == null && this.description == null;
	}

}
