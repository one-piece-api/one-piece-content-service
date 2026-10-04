package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEmbeddable;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Between the rows of a Devil Fruit Type version - its workflow in the shared table, what
 * it says in the entity's own - and the domain.
 */
@UtilityClass
public class DevilFruitTypeVersionMapper {

	public Version<DevilFruitType> toDomain(DevilFruitTypeVersionEntity entity) {
		var body = new DevilFruitType(entity.getRomaji(), toDomain(entity.getTranslations()));
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	/**
	 * The first version of a new content: a draft of its author, saying what was given.
	 */
	public DevilFruitTypeVersionEntity toFirstDraft(UUID contentId, User author, DevilFruitType body, Instant now) {
		return toDraft(contentId, 1, null, author, body, now);
	}

	/**
	 * A later version of a content, opened from one of its versions: a draft of its
	 * author, saying what that one says.
	 * @param number the number the new version takes
	 */
	public DevilFruitTypeVersionEntity toDraftFrom(UUID contentId, int number, Version<DevilFruitType> base,
			User author, Instant now) {
		return toDraft(contentId, number, base.number(), author, base.body(), now);
	}

	private static DevilFruitTypeVersionEntity toDraft(UUID contentId, int number, Integer basedOn, User author,
			DevilFruitType body, Instant now) {
		ContentVersionEntity workflow = ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(number)
			.basedOnNumber(basedOn)
			.author(UserMapper.toEmbeddable(author))
			.status(VersionStatus.DRAFT)
			.createdAt(now)
			.updatedAt(now)
			.build();
		var entity = new DevilFruitTypeVersionEntity(workflow);
		rewrite(entity, body, now);
		return entity;
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(DevilFruitTypeVersionEntity entity, DevilFruitType body, Instant now) {
		entity.rewrite(body.romaji(), toEmbeddables(body.translations()), now);
	}

	/** Sorted by language code, so the same version always reads the same way. */
	private static Map<String, DevilFruitTypeTranslation> toDomain(Map<String, TranslationEmbeddable> translations) {
		Map<String, DevilFruitTypeTranslation> byLanguage = new TreeMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language, toDomain(translation)));
		return byLanguage;
	}

	private static DevilFruitTypeTranslation toDomain(TranslationEmbeddable translation) {
		return new DevilFruitTypeTranslation(translation.getName(), translation.getDescription(),
				translation.getAdvantages(), translation.getDisadvantages());
	}

	private static Map<String, TranslationEmbeddable> toEmbeddables(
			Map<String, DevilFruitTypeTranslation> translations) {
		Map<String, TranslationEmbeddable> byLanguage = new HashMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language, toEmbeddable(translation)));
		return byLanguage;
	}

	private static TranslationEmbeddable toEmbeddable(DevilFruitTypeTranslation translation) {
		return new TranslationEmbeddable(translation.name(), translation.description(), translation.advantages(),
				translation.disadvantages());
	}

}
