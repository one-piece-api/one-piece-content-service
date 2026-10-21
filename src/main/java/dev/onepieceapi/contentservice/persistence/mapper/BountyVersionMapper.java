package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyTranslation;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.BountyFields;
import dev.onepieceapi.contentservice.persistence.entity.BountyTranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.BountyVersionEntity;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Between the rows of a bounty version - its workflow in the shared table, what it says
 * in the entity's own - and the domain.
 */
@UtilityClass
public class BountyVersionMapper {

	public Version<Bounty> toDomain(BountyVersionEntity entity) {
		var body = new Bounty(entity.getPosterName(), entity.getAmount(), entity.getCondition(), entity.isInactive(),
				entity.getPreviousContentId(), entity.getHolderContentId(), toDomain(entity.getTranslations()),
				entity.getImageId(),
				new StoryPoint(entity.getFirstAppearanceChapterId(), entity.getFirstAppearanceEpisodeId()));
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(BountyVersionEntity entity, Bounty body, Instant now) {
		entity.rewrite(BountyFields.builder()
			.posterName(body.posterName())
			.amount(body.amount())
			.condition(body.condition())
			.inactive(body.inactive())
			.previousContentId(body.previousContentId())
			.holderContentId(body.holderContentId())
			.translations(toEmbeddables(body.translations()))
			.imageId(body.imageId())
			.firstAppearance(FirstAppearanceMapper.toIds(body.firstAppearance()))
			.build(), now);
	}

	/** Sorted by language code, so the same version always reads the same way. */
	private static Map<String, BountyTranslation> toDomain(Map<String, BountyTranslationEmbeddable> translations) {
		Map<String, BountyTranslation> byLanguage = new TreeMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language,
				new BountyTranslation(translation.getDescription())));
		return byLanguage;
	}

	private static Map<String, BountyTranslationEmbeddable> toEmbeddables(Map<String, BountyTranslation> translations) {
		Map<String, BountyTranslationEmbeddable> byLanguage = new HashMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language,
				new BountyTranslationEmbeddable(translation.description())));
		return byLanguage;
	}

}
