package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategory;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategoryTranslation;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeSubcategoryEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.SubcategoryTranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEmbeddable;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Between the rows of a Devil Fruit Type version - its workflow in the shared table, what
 * it says in the entity's own - and the domain.
 */
@UtilityClass
public class DevilFruitTypeVersionMapper {

	public Version<DevilFruitType> toDomain(DevilFruitTypeVersionEntity entity) {
		var body = new DevilFruitType(entity.getRomaji(), toDomain(entity.getTranslations()),
				entity.getSubcategories().stream().map(DevilFruitTypeVersionMapper::toDomain).toList());
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
		var workflow = ContentVersionMapper.toDraft(contentId, number, basedOn, author, now);
		var entity = new DevilFruitTypeVersionEntity(workflow);
		rewrite(entity, body, now);
		return entity;
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(DevilFruitTypeVersionEntity entity, DevilFruitType body, Instant now) {
		entity.rewrite(body.romaji(), toEmbeddables(body.translations()), toSubcategoryRows(body.subcategories()), now);
	}

	private static DevilFruitTypeSubcategory toDomain(DevilFruitTypeSubcategoryEntity subcategory) {
		Map<String, DevilFruitTypeSubcategoryTranslation> byLanguage = new TreeMap<>();
		subcategory.getTranslations()
			.forEach((language, translation) -> byLanguage.put(language,
					new DevilFruitTypeSubcategoryTranslation(translation.getName(), translation.getDescription())));
		return new DevilFruitTypeSubcategory(subcategory.getSubcategoryId(), byLanguage);
	}

	/**
	 * The translations of each subcategory by its id, in order; every one has an id by
	 * now (see {@code ContentBody#identified}).
	 */
	private static Map<UUID, Map<String, SubcategoryTranslationEmbeddable>> toSubcategoryRows(
			List<DevilFruitTypeSubcategory> subcategories) {
		Map<UUID, Map<String, SubcategoryTranslationEmbeddable>> rows = new LinkedHashMap<>();
		subcategories.forEach(subcategory -> {
			Map<String, SubcategoryTranslationEmbeddable> byLanguage = new HashMap<>();
			subcategory.translations()
				.forEach((language, translation) -> byLanguage.put(language,
						new SubcategoryTranslationEmbeddable(translation.name(), translation.description())));
			rows.put(Objects.requireNonNull(subcategory.id(), "a subcategory is saved with its id"), byLanguage);
		});
		return rows;
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
