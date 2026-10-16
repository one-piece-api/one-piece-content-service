package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReveal;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitRevealEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitRevealTranslationEmbeddable;
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
		var body = new DevilFruit(entity.getRomaji(), entity.getTypeContentId(), entity.getSubcategoryId(),
				toDomain(entity.getTranslations()), entity.getImageId(),
				new StoryPoint(entity.getFirstAppearanceChapterId()),
				entity.getReveals().stream().map(DevilFruitVersionMapper::toDomain).toList());
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(DevilFruitVersionEntity entity, DevilFruit body, Instant now) {
		entity.rewrite(body.romaji(), body.typeContentId(), body.subcategoryId(), toEmbeddables(body.translations()),
				body.imageId(), body.firstAppearanceChapterId(), now);
		entity.rewriteReveals(body.reveals().stream().map(DevilFruitVersionMapper::toWritten).toList());
	}

	private static DevilFruitReveal toDomain(DevilFruitRevealEntity reveal) {
		return new DevilFruitReveal(new StoryPoint(reveal.getRevealedAtChapterId()), reveal.getRomaji(),
				reveal.getTypeContentId(), reveal.getSubcategoryId(), believed(reveal.getTranslations()),
				titles(reveal.getTranslations()));
	}

	private static DevilFruitRevealEntity.Written toWritten(DevilFruitReveal reveal) {
		return new DevilFruitRevealEntity.Written(reveal.revealedAtChapterId(), reveal.romaji(), reveal.typeContentId(),
				reveal.subcategoryId(), toRevealEmbeddables(reveal));
	}

	/** The believed fields of a reveal, without the languages that hold a title only. */
	private static Map<String, DevilFruitTranslation> believed(
			Map<String, DevilFruitRevealTranslationEmbeddable> translations) {
		Map<String, DevilFruitTranslation> byLanguage = new TreeMap<>();
		translations.forEach((language, translation) -> {
			var believed = new DevilFruitTranslation(translation.getName(), translation.getDescription(),
					translation.getAdvantages(), translation.getDisadvantages());
			if (!believed.isEmpty()) {
				byLanguage.put(language, believed);
			}
		});
		return byLanguage;
	}

	private static Map<String, String> titles(Map<String, DevilFruitRevealTranslationEmbeddable> translations) {
		Map<String, String> byLanguage = new TreeMap<>();
		translations.forEach((language, translation) -> {
			if (translation.getTitle() != null) {
				byLanguage.put(language, translation.getTitle());
			}
		});
		return byLanguage;
	}

	/** A row per language with a believed field or a title. */
	private static Map<String, DevilFruitRevealTranslationEmbeddable> toRevealEmbeddables(DevilFruitReveal reveal) {
		Map<String, DevilFruitRevealTranslationEmbeddable> byLanguage = new HashMap<>();
		reveal.languages().forEach(language -> {
			DevilFruitTranslation believed = reveal.translationIn(language);
			byLanguage.put(language, new DevilFruitRevealTranslationEmbeddable(believed.name(), believed.description(),
					believed.advantages(), believed.disadvantages(), reveal.titleIn(language)));
		});
		return byLanguage;
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
