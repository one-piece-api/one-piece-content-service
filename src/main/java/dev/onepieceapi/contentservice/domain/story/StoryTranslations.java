package dev.onepieceapi.contentservice.domain.story;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What every story entity does with its translations, written once: tidied up, read as
 * titles, checked for completeness.
 */
@UtilityClass
class StoryTranslations {

	/** Named as request validation names them, so a client reads both the same way. */
	static final String TITLE = "title";

	private static final String TITLE_FIELD = "translations[%s].title";

	private static final String DESCRIPTION_FIELD = "translations[%s].description";

	/** Each one tidied up, a language with nothing left in it dropped. */
	Map<String, StoryTranslation> normalized(Map<String, StoryTranslation> translations) {
		Map<String, StoryTranslation> written = new TreeMap<>();
		translations.forEach((language, translation) -> {
			StoryTranslation normalized = translation.normalized();
			if (!normalized.isEmpty()) {
				written.put(language, normalized);
			}
		});
		return written;
	}

	/** The title per language code, for the languages that have one. */
	Map<String, String> titles(Map<String, StoryTranslation> translations) {
		Map<String, String> titles = new TreeMap<>();
		translations.forEach((language, translation) -> {
			if (translation.title() != null) {
				titles.put(language, translation.title());
			}
		});
		return titles;
	}

	/** A title and a description per language, each one missing added to the list. */
	void addMissing(Map<String, StoryTranslation> translations, Collection<String> languages, List<String> missing) {
		for (String language : languages) {
			StoryTranslation translation = translations.getOrDefault(language, StoryTranslation.NONE);
			if (translation.title() == null) {
				missing.add(TITLE_FIELD.formatted(language));
			}
			if (translation.description() == null) {
				missing.add(DESCRIPTION_FIELD.formatted(language));
			}
		}
	}

}
