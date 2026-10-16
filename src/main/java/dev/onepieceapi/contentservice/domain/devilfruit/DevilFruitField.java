package dev.onepieceapi.contentservice.domain.devilfruit;

import java.util.Locale;
import java.util.function.Function;

/**
 * A field of a Devil Fruit the reader may learn in steps (implementation plan of the
 * timeline, T13): what a reveal names as learned at its chapter. The type stands for the
 * subcategory too, since the two are believed together (ADR-0010). The image never is.
 */
public enum DevilFruitField {

	NAME(DevilFruitTranslation::name), ROMAJI(null), TYPE(null), DESCRIPTION(DevilFruitTranslation::description),
	ADVANTAGES(DevilFruitTranslation::advantages), DISADVANTAGES(DevilFruitTranslation::disadvantages);

	private final Function<DevilFruitTranslation, String> translated;

	DevilFruitField(Function<DevilFruitTranslation, String> translated) {
		this.translated = translated;
	}

	/** Whether it is written per language. */
	public boolean isTranslated() {
		return this.translated != null;
	}

	/** Its value in a translation; only for a translated field. */
	public String in(DevilFruitTranslation translation) {
		return this.translated.apply(translation);
	}

	/** Its name as the requests have it, e.g. {@code name}. */
	public String fieldName() {
		return name().toLowerCase(Locale.ROOT);
	}

}
