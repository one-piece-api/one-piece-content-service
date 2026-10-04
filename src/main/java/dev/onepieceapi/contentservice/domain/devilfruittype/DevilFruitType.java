package dev.onepieceapi.contentservice.domain.devilfruittype;

import java.util.Map;
import java.util.TreeMap;

/**
 * What one version of a Devil Fruit Type says
 * (docs/user-flows/content-editorial-workflow.md 3.1): one romaji shared by every
 * language, and a name, description, advantages and disadvantages per language code.
 * Plugged into the generic {@code Content} and {@code Version}, which carry the workflow
 * around it.
 */
public record DevilFruitType(String romaji, Map<String, DevilFruitTypeTranslation> translations) {

	public static final int ROMAJI_MAX_LENGTH = 100;

	/**
	 * The same content as it is stored and compared: no space around a text, a blank text
	 * counted as missing, and no translation left for a language nothing was written in.
	 */
	public DevilFruitType normalized() {
		Map<String, DevilFruitTypeTranslation> written = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			DevilFruitTypeTranslation normalized = translation.normalized();
			if (!normalized.isEmpty()) {
				written.put(language, normalized);
			}
		});
		return new DevilFruitType(Text.stripToNull(this.romaji), written);
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
	public DevilFruitTypeTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, DevilFruitTypeTranslation.NONE);
	}

}
