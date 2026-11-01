package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.workflow.Text;

/**
 * The localized field of a Devil Fruit in one language (implementation plan of the Devil
 * Fruit, D7): its name, which may still be missing while the version is a draft. The
 * description, advantages and disadvantages are lists of paragraphs (workflow 3.7.3).
 */
public record DevilFruitTranslation(String name) {

	public static final int NAME_MAX_LENGTH = 100;

	/** A language nothing was written in. */
	static final DevilFruitTranslation NONE = new DevilFruitTranslation(null);

	DevilFruitTranslation normalized() {
		return new DevilFruitTranslation(Text.stripToNull(this.name));
	}

	/** Nothing written in this language. */
	boolean isEmpty() {
		return this.name == null;
	}

}
