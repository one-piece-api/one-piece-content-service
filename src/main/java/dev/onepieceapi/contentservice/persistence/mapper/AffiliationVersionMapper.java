package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationTranslation;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationTranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationVersionEntity;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Between the rows of an Affiliation version - its workflow in the shared table, what it
 * says in the entity's own - and the domain.
 */
@UtilityClass
public class AffiliationVersionMapper {

	public Version<Affiliation> toDomain(AffiliationVersionEntity entity) {
		var body = new Affiliation(entity.getRomaji(), toDomain(entity.getTranslations()), entity.getImageId(),
				new StoryPoint(entity.getFirstAppearanceChapterId(), entity.getFirstAppearanceEpisodeId()),
				entity.getKind());
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(AffiliationVersionEntity entity, Affiliation body, Instant now) {
		entity.rewrite(body.romaji(), toEmbeddables(body.translations()), body.imageId(),
				FirstAppearanceMapper.toIds(body.firstAppearance()), body.kind(), now);
	}

	/** Sorted by language code, so the same version always reads the same way. */
	private static Map<String, AffiliationTranslation> toDomain(
			Map<String, AffiliationTranslationEmbeddable> translations) {
		Map<String, AffiliationTranslation> byLanguage = new TreeMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language,
				new AffiliationTranslation(translation.getName(), translation.getDescription())));
		return byLanguage;
	}

	private static Map<String, AffiliationTranslationEmbeddable> toEmbeddables(
			Map<String, AffiliationTranslation> translations) {
		Map<String, AffiliationTranslationEmbeddable> byLanguage = new HashMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language,
				new AffiliationTranslationEmbeddable(translation.name(), translation.description())));
		return byLanguage;
	}

}
