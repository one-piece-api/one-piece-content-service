package dev.onepieceapi.contentservice.domain.devilfruittype;

import dev.onepieceapi.contentservice.domain.workflow.Text;

/**
 * The localized field of a Devil Fruit Type in one language
 * (docs/user-flows/content-editorial-workflow.md 3.1): its name, which may still be
 * missing while the version is a draft. The description, advantages and disadvantages are
 * lists of paragraphs (3.7.3).
 */
public record DevilFruitTypeTranslation(String name) {

	public static final int NAME_MAX_LENGTH = 100;

	/** A language nothing was written in. */
	static final DevilFruitTypeTranslation NONE = new DevilFruitTypeTranslation(null);

	DevilFruitTypeTranslation normalized() {
		return new DevilFruitTypeTranslation(Text.stripToNull(this.name));
	}

	/** Nothing written in this language. */
	boolean isEmpty() {
		return this.name == null;
	}

}
