package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Values a reader holds of a Devil Fruit that are not the truth yet (implementation plan
 * of the timeline, T13): what is known at the first appearance, or what an intermediate
 * reveal makes known. Only some fields are held; a null one is not part of it. The type
 * and the subcategory go together: a subcategory names one of this type's.
 *
 * @param typeContentId the content of the Devil Fruit Type; null when not part of it
 * @param subcategoryId a subcategory of that type; null for none
 * @param translations the translated values per language code
 */
public record DevilFruitBelief(String romaji, UUID typeContentId, UUID subcategoryId,
		Map<String, DevilFruitTranslation> translations) {

	/** Nothing held. */
	public static final DevilFruitBelief NONE = new DevilFruitBelief(null, null, null, Map.of());

	/** No translation is an empty map. */
	public DevilFruitBelief {
		translations = translations == null ? Map.of() : translations;
	}

	DevilFruitBelief normalized() {
		Map<String, DevilFruitTranslation> written = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			DevilFruitTranslation normalized = translation.normalized();
			if (!normalized.isEmpty()) {
				written.put(language, normalized);
			}
		});
		return new DevilFruitBelief(Text.stripToNull(this.romaji), this.typeContentId, this.subcategoryId, written);
	}

	/** Whether it holds nothing at all. */
	public boolean isEmpty() {
		return this.romaji == null && this.typeContentId == null && this.subcategoryId == null
				&& this.translations.values().stream().allMatch(DevilFruitTranslation::isEmpty);
	}

	/**
	 * Whether it holds a value for the field - in some language, for a translated one;
	 * the type when it names a type or a subcategory.
	 */
	public boolean holds(DevilFruitField field) {
		return switch (field) {
			case ROMAJI -> this.romaji != null;
			case TYPE -> this.typeContentId != null || this.subcategoryId != null;
			default -> this.translations.values().stream().anyMatch(translation -> field.in(translation) != null);
		};
	}

	/** Whether it holds a translated field in this language. */
	public boolean holds(DevilFruitField field, String language) {
		return field.in(translationIn(language)) != null;
	}

	/**
	 * What it says in this language - nothing at all, when no translation was written.
	 */
	public DevilFruitTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, DevilFruitTranslation.NONE);
	}

	public Set<String> languages() {
		return this.translations.keySet();
	}

}
