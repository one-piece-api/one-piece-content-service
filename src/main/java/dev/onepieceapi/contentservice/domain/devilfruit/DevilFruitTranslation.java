package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * The localized fields of a Devil Fruit in one language (implementation plan of the Devil
 * Fruit, D7). Any of them may still be missing while the version is a draft.
 */
public record DevilFruitTranslation(String name, String description, String advantages, String disadvantages) {

	public static final int NAME_MAX_LENGTH = 100;

	public static final int DESCRIPTION_MAX_LENGTH = 2000;

	public static final int ADVANTAGES_MAX_LENGTH = 2000;

	public static final int DISADVANTAGES_MAX_LENGTH = 2000;

	/** A language nothing was written in. */
	static final DevilFruitTranslation NONE = new DevilFruitTranslation(null, null, null, null);

	DevilFruitTranslation normalized() {
		return new DevilFruitTranslation(Text.stripToNull(this.name), Text.stripToNull(this.description),
				Text.stripToNull(this.advantages), Text.stripToNull(this.disadvantages));
	}

	/** Nothing written in this language. */
	boolean isEmpty() {
		return Stream.of(this.name, this.description, this.advantages, this.disadvantages).allMatch(Objects::isNull);
	}

}
