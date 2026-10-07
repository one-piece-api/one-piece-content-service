package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * What one version of a Devil Fruit says (implementation plan of the Devil Fruit, D1,
 * D7): one romaji shared by every language, the Devil Fruit Type it belongs to, and a
 * name, description, advantages and disadvantages per language code. Plugged into the
 * generic {@code Content} and {@code Version}, which carry the workflow around it.
 * <p>
 * The type is an id - the type's content, not one of its versions - and is missing while
 * the version is an incomplete draft. Two versions saying the same except for the type
 * are different, as the record's equality has it.
 *
 * @param typeContentId the content of the Devil Fruit Type; null while not chosen
 */
public record DevilFruit(String romaji, UUID typeContentId,
		Map<String, DevilFruitTranslation> translations) implements ContentBody<DevilFruit> {

	public static final int ROMAJI_MAX_LENGTH = 100;

	private static final String ROMAJI_FIELD = "romaji";

	private static final String TYPE_FIELD = "type";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String NAME_FIELD = "translations[%s].name";

	private static final String DESCRIPTION_FIELD = "translations[%s].description";

	private static final String ADVANTAGES_FIELD = "translations[%s].advantages";

	private static final String DISADVANTAGES_FIELD = "translations[%s].disadvantages";

	@Override
	public DevilFruit normalized() {
		Map<String, DevilFruitTranslation> written = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			DevilFruitTranslation normalized = translation.normalized();
			if (!normalized.isEmpty()) {
				written.put(language, normalized);
			}
		});
		return new DevilFruit(Text.stripToNull(this.romaji), this.typeContentId, written);
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
	 * The romaji and the type, then a name, a description, advantages and disadvantages
	 * per language - every one missing.
	 */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.romaji == null) {
			missing.add(ROMAJI_FIELD);
		}
		if (this.typeContentId == null) {
			missing.add(TYPE_FIELD);
		}
		for (String language : languages) {
			DevilFruitTranslation translation = translationIn(language);
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
	public DevilFruitTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, DevilFruitTranslation.NONE);
	}

}
