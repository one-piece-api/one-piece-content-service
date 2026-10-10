package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;
import java.util.UUID;

/**
 * A subcategory of a Devil Fruit Type, as its version says it.
 *
 * @param id the same in every version of the type that keeps it
 * @param translations name and description per language code
 */
public record DevilFruitTypeSubcategoryResponse(UUID id,
		Map<String, DevilFruitTypeSubcategoryTranslationResponse> translations) {

}
