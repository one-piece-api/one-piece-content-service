package dev.onepieceapi.contentservice.domain.devilfruittype;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A finer group inside a Devil Fruit Type, e.g. Ancient inside Zoan
 * (docs/user-flows/content-editorial-workflow.md 3.1.1): part of the type's version, with
 * a name and a description per language. Its id stays the same in every version of the
 * type that keeps it, and is what a fruit points to.
 *
 * @param id null for a subcategory just added, until the version is saved
 * @param translations name and description per language code
 */
public record DevilFruitTypeSubcategory(UUID id, Map<String, DevilFruitTypeSubcategoryTranslation> translations) {

	DevilFruitTypeSubcategory normalized() {
		Map<String, DevilFruitTypeSubcategoryTranslation> written = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			DevilFruitTypeSubcategoryTranslation normalized = translation.normalized();
			if (!normalized.isEmpty()) {
				written.put(language, normalized);
			}
		});
		return new DevilFruitTypeSubcategory(this.id, written);
	}

	/** The same subcategory with an id: its own, or a new one when it has none yet. */
	DevilFruitTypeSubcategory identified() {
		return this.id != null ? this : new DevilFruitTypeSubcategory(UUID.randomUUID(), this.translations);
	}

	/** The name per language code, for the languages that have one. */
	public Map<String, String> names() {
		Map<String, String> names = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			if (translation.name() != null) {
				names.put(language, translation.name());
			}
		});
		return names;
	}

	/**
	 * What it says in this language - nothing at all, when no translation was written.
	 */
	public DevilFruitTypeSubcategoryTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, DevilFruitTypeSubcategoryTranslation.NONE);
	}

}
