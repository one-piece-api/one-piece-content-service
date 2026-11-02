package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitField;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitKnowledge;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitFields;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitRevealEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEmbeddable;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Between the rows of a Devil Fruit version - its workflow in the shared table, what it
 * says in the entity's own, its initial knowledge and reveals - and the domain.
 */
@UtilityClass
public class DevilFruitVersionMapper {

	public Version<DevilFruit> toDomain(DevilFruitVersionEntity entity) {
		var paragraphs = ParagraphMapper.toDomain(entity.getParagraphs());
		var body = DevilFruit.builder()
			.romaji(entity.getRomaji())
			.typeContentId(entity.getTypeContentId())
			.subcategoryId(entity.getSubcategoryId())
			.translations(toDomain(entity.getTranslations()))
			.imageId(entity.getImageId())
			.firstAppearance(new StoryPoint(entity.getFirstAppearanceChapterId(), entity.getFirstAppearanceEpisodeId()))
			.multipleUsers(entity.isMultipleUsers())
			.description(paragraphs.get(ParagraphSection.DESCRIPTION))
			.advantages(paragraphs.get(ParagraphSection.ADVANTAGES))
			.disadvantages(paragraphs.get(ParagraphSection.DISADVANTAGES))
			.initialKnowledge(initialKnowledgeOf(entity))
			.reveals(entity.getReveals().stream().map(DevilFruitVersionMapper::toDomain).toList())
			.build();
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(DevilFruitVersionEntity entity, DevilFruit body, Instant now) {
		DevilFruitKnowledge initial = body.initialKnowledge();
		entity.rewrite(DevilFruitFields.builder()
			.romaji(body.romaji())
			.typeContentId(body.typeContentId())
			.subcategoryId(body.subcategoryId())
			.translations(toEmbeddables(body.translations()))
			.imageId(body.imageId())
			.firstAppearance(FirstAppearanceMapper.toIds(body.firstAppearance()))
			.multipleUsers(body.multipleUsers())
			.paragraphs(ParagraphMapper.toFields(body.paragraphs()))
			.initialRomaji(initial.romaji())
			.initialTypeContentId(initial.typeContentId())
			.initialSubcategoryId(initial.subcategoryId())
			.initialTranslations(namesToEmbeddables(initial.names()))
			.reveals(body.reveals().stream().map(DevilFruitVersionMapper::toWritten).toList())
			.build(), now);
	}

	private static DevilFruitKnowledge initialKnowledgeOf(DevilFruitVersionEntity entity) {
		Map<String, String> names = new TreeMap<>();
		entity.getInitialTranslations().forEach((language, translation) -> names.put(language, translation.getName()));
		return new DevilFruitKnowledge(entity.getInitialRomaji(), entity.getInitialTypeContentId(),
				entity.getInitialSubcategoryId(), names);
	}

	private static Reveal<DevilFruitField, DevilFruitKnowledge> toDomain(DevilFruitRevealEntity reveal) {
		var values = new DevilFruitKnowledge(reveal.getRomaji(), reveal.getTypeContentId(), reveal.getSubcategoryId(),
				RevealMapper.namesOf(reveal.getTranslations()));
		return new Reveal<>(RevealMapper.pointOf(reveal), reveal.getFields(), values,
				RevealMapper.titlesOf(reveal.getTranslations()));
	}

	private static DevilFruitRevealEntity.Written toWritten(Reveal<DevilFruitField, DevilFruitKnowledge> reveal) {
		DevilFruitKnowledge values = reveal.values();
		return DevilFruitRevealEntity.Written.builder()
			.point(FirstAppearanceMapper.toIds(reveal.revealedAt()))
			.fields(reveal.fields())
			.romaji(values.romaji())
			.typeContentId(values.typeContentId())
			.subcategoryId(values.subcategoryId())
			.translations(RevealMapper.toTranslations(reveal.titles(), values.names()))
			.build();
	}

	/** Sorted by language code, so the same version always reads the same way. */
	private static Map<String, DevilFruitTranslation> toDomain(Map<String, TranslationEmbeddable> translations) {
		Map<String, DevilFruitTranslation> byLanguage = new TreeMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language, toDomain(translation)));
		return byLanguage;
	}

	private static DevilFruitTranslation toDomain(TranslationEmbeddable translation) {
		return new DevilFruitTranslation(translation.getName());
	}

	private static Map<String, TranslationEmbeddable> toEmbeddables(Map<String, DevilFruitTranslation> translations) {
		Map<String, TranslationEmbeddable> byLanguage = new HashMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language, toEmbeddable(translation)));
		return byLanguage;
	}

	private static TranslationEmbeddable toEmbeddable(DevilFruitTranslation translation) {
		return new TranslationEmbeddable(translation.name());
	}

	private static Map<String, TranslationEmbeddable> namesToEmbeddables(Map<String, String> names) {
		Map<String, TranslationEmbeddable> byLanguage = new HashMap<>();
		names.forEach((language, name) -> byLanguage.put(language, new TranslationEmbeddable(name)));
		return byLanguage;
	}

}
