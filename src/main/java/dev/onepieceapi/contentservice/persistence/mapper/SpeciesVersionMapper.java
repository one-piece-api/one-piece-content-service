package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.domain.species.SpeciesTranslation;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.SpeciesTranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.SpeciesVersionEntity;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Between the rows of a Species version - its workflow in the shared table, what it says
 * in the entity's own - and the domain.
 */
@UtilityClass
public class SpeciesVersionMapper {

	public Version<Species> toDomain(SpeciesVersionEntity entity) {
		var body = new Species(entity.getRomaji(), toDomain(entity.getTranslations()), entity.getImageId(),
				new StoryPoint(entity.getFirstAppearanceChapterId(), entity.getFirstAppearanceEpisodeId()));
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(SpeciesVersionEntity entity, Species body, Instant now) {
		entity.rewrite(body.romaji(), toEmbeddables(body.translations()), body.imageId(),
				FirstAppearanceMapper.toIds(body.firstAppearance()), now);
	}

	/** Sorted by language code, so the same version always reads the same way. */
	private static Map<String, SpeciesTranslation> toDomain(Map<String, SpeciesTranslationEmbeddable> translations) {
		Map<String, SpeciesTranslation> byLanguage = new TreeMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language,
				new SpeciesTranslation(translation.getName(), translation.getDescription())));
		return byLanguage;
	}

	private static Map<String, SpeciesTranslationEmbeddable> toEmbeddables(
			Map<String, SpeciesTranslation> translations) {
		Map<String, SpeciesTranslationEmbeddable> byLanguage = new HashMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language,
				new SpeciesTranslationEmbeddable(translation.name(), translation.description())));
		return byLanguage;
	}

}
