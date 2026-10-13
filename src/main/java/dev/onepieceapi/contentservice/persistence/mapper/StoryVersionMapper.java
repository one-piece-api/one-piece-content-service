package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.ArcVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.SagaVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.StoryTranslationEmbeddable;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Between the rows of a story version - saga, arc - and the domain: the workflow in the
 * shared table, what it says in the entity's own.
 */
@UtilityClass
public class StoryVersionMapper {

	public Version<Saga> toDomain(SagaVersionEntity entity) {
		var body = new Saga(entity.getNumber(), toDomain(entity.getTranslations()));
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	public Version<Arc> toDomain(ArcVersionEntity entity) {
		var body = new Arc(entity.getNumber(), entity.getSagaContentId(), toDomain(entity.getTranslations()));
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(SagaVersionEntity entity, Saga body, Instant now) {
		entity.rewrite(body.number(), toEmbeddables(body.translations()), now);
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(ArcVersionEntity entity, Arc body, Instant now) {
		entity.rewrite(body.number(), body.sagaContentId(), toEmbeddables(body.translations()), now);
	}

	/** Sorted by language code, so the same version always reads the same way. */
	private static Map<String, StoryTranslation> toDomain(Map<String, StoryTranslationEmbeddable> translations) {
		Map<String, StoryTranslation> byLanguage = new TreeMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language,
				new StoryTranslation(translation.getName(), translation.getDescription())));
		return byLanguage;
	}

	private static Map<String, StoryTranslationEmbeddable> toEmbeddables(Map<String, StoryTranslation> translations) {
		Map<String, StoryTranslationEmbeddable> byLanguage = new HashMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language,
				new StoryTranslationEmbeddable(translation.title(), translation.description())));
		return byLanguage;
	}

}
