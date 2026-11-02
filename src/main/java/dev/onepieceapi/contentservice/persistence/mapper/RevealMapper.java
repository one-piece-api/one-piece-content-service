package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.persistence.entity.RevealEntity;
import dev.onepieceapi.contentservice.persistence.entity.RevealTranslationEmbeddable;
import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What every reveal row shares (workflow 3.7.1): its point, and its title and learned
 * name per language, one translation row holding both.
 */
@UtilityClass
public class RevealMapper {

	public StoryPoint pointOf(RevealEntity reveal) {
		return new StoryPoint(reveal.getRevealedChapterId(), reveal.getRevealedEpisodeId());
	}

	/** One row per language with a title or a name. */
	public Map<String, RevealTranslationEmbeddable> toTranslations(Map<String, String> titles,
			Map<String, String> names) {
		var languages = new TreeSet<>(titles.keySet());
		languages.addAll(names.keySet());
		Map<String, RevealTranslationEmbeddable> byLanguage = new HashMap<>();
		languages.forEach((language) -> byLanguage.put(language,
				new RevealTranslationEmbeddable(titles.get(language), names.get(language))));
		return byLanguage;
	}

	/** The titles written, sorted by language code. */
	public Map<String, String> titlesOf(Map<String, RevealTranslationEmbeddable> translations) {
		return valuesOf(translations, RevealTranslationEmbeddable::getTitle);
	}

	/** The names learned, sorted by language code. */
	public Map<String, String> namesOf(Map<String, RevealTranslationEmbeddable> translations) {
		return valuesOf(translations, RevealTranslationEmbeddable::getName);
	}

	private static Map<String, String> valuesOf(Map<String, RevealTranslationEmbeddable> translations,
			Function<RevealTranslationEmbeddable, String> value) {
		return translations.entrySet()
			.stream()
			.filter((entry) -> Objects.nonNull(value.apply(entry.getValue())))
			.collect(Collectors.toMap(Map.Entry::getKey, (entry) -> value.apply(entry.getValue()), (a, b) -> a,
					TreeMap::new));
	}

}
