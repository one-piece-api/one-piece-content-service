package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEmbeddable;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Between the rows of a Devil Fruit version - its workflow in the shared table, what it
 * says in the entity's own - and the domain.
 */
@UtilityClass
public class DevilFruitVersionMapper {

	public Version<DevilFruit> toDomain(DevilFruitVersionEntity entity) {
		var body = new DevilFruit(entity.getRomaji(), entity.getTypeContentId(), toDomain(entity.getTranslations()),
				entity.getImageId());
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(DevilFruitVersionEntity entity, DevilFruit body, Instant now) {
		entity.rewrite(body.romaji(), body.typeContentId(), toEmbeddables(body.translations()), body.imageId(), now);
	}

	/** Sorted by language code, so the same version always reads the same way. */
	private static Map<String, DevilFruitTranslation> toDomain(Map<String, TranslationEmbeddable> translations) {
		Map<String, DevilFruitTranslation> byLanguage = new TreeMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language, toDomain(translation)));
		return byLanguage;
	}

	private static DevilFruitTranslation toDomain(TranslationEmbeddable translation) {
		return new DevilFruitTranslation(translation.getName(), translation.getDescription(),
				translation.getAdvantages(), translation.getDisadvantages());
	}

	private static Map<String, TranslationEmbeddable> toEmbeddables(Map<String, DevilFruitTranslation> translations) {
		Map<String, TranslationEmbeddable> byLanguage = new HashMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language, toEmbeddable(translation)));
		return byLanguage;
	}

	private static TranslationEmbeddable toEmbeddable(DevilFruitTranslation translation) {
		return new TranslationEmbeddable(translation.name(), translation.description(), translation.advantages(),
				translation.disadvantages());
	}

}
