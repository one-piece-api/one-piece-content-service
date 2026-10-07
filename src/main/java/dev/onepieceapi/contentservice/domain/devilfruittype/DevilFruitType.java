package dev.onepieceapi.contentservice.domain.devilfruittype;

import dev.onepieceapi.contentservice.domain.workflow.ContentBody;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * What one version of a Devil Fruit Type says
 * (docs/user-flows/content-editorial-workflow.md 3.1.1): one romaji shared by every
 * language, and a name, description, advantages and disadvantages per language code.
 * Plugged into the generic {@code Content} and {@code Version}, which carry the workflow
 * around it.
 */
public record DevilFruitType(String romaji,
		Map<String, DevilFruitTypeTranslation> translations) implements ContentBody<DevilFruitType> {

	public static final int ROMAJI_MAX_LENGTH = 100;

	private static final String ROMAJI_FIELD = "romaji";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String NAME_FIELD = "translations[%s].name";

	private static final String DESCRIPTION_FIELD = "translations[%s].description";

	private static final String ADVANTAGES_FIELD = "translations[%s].advantages";

	private static final String DISADVANTAGES_FIELD = "translations[%s].disadvantages";

	@Override
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

	@Override
	public Set<String> languages() {
		return this.translations.keySet();
	}

	@Override
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
	 * The romaji, then a name, a description, advantages and disadvantages per language -
	 * every one missing.
	 */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.romaji == null) {
			missing.add(ROMAJI_FIELD);
		}
		for (String language : languages) {
			DevilFruitTypeTranslation translation = translationIn(language);
			if (translation.name() == null) {
				missing.add(NAME_FIELD.formatted(language));
			}
			if (translation.description() == null) {
				missing.add(DESCRIPTION_FIELD.formatted(language));
			}
			if (translation.advantages() == null) {
				missing.add(ADVANTAGES_FIELD.formatted(language));
			}
			if (translation.disadvantages() == null) {
				missing.add(DISADVANTAGES_FIELD.formatted(language));
			}
		}
		return missing;
	}

	/**
	 * What it says in this language - nothing at all, when no translation was written.
	 */
	public DevilFruitTypeTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, DevilFruitTypeTranslation.NONE);
	}

}
