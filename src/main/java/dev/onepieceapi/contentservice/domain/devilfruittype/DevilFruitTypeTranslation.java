package dev.onepieceapi.contentservice.domain.devilfruittype;

/**
 * The localized fields of a Devil Fruit Type in one language
 * (docs/user-flows/content-editorial-workflow.md 3.1). Either may still be missing while
 * the version is a draft.
 */
public record DevilFruitTypeTranslation(String name, String description) {

	public static final int NAME_MAX_LENGTH = 100;

	public static final int DESCRIPTION_MAX_LENGTH = 2000;

	/** A language nothing was written in. */
	static final DevilFruitTypeTranslation NONE = new DevilFruitTypeTranslation(null, null);

	DevilFruitTypeTranslation normalized() {
		return new DevilFruitTypeTranslation(Text.stripToNull(this.name), Text.stripToNull(this.description));
	}

	/** Nothing written in this language. */
	boolean isEmpty() {
		return this.name == null && this.description == null;
	}

}
