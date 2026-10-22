package dev.onepieceapi.contentservice.persistence.entity;

import lombok.Builder;

import java.util.Map;
import java.util.UUID;

/**
 * Everything a Devil Fruit version says, as its row is written - what
 * {@link DevilFruitVersionEntity#rewrite} replaces it with.
 */
@Builder
public record DevilFruitFields(String romaji, UUID typeContentId, UUID subcategoryId,
		Map<String, TranslationEmbeddable> translations, String imageId, FirstAppearanceIds firstAppearance,
		boolean multipleUsers) {

}
