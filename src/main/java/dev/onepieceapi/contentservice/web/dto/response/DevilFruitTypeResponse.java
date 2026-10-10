package dev.onepieceapi.contentservice.web.dto.response;

import java.util.List;
import java.util.Map;

/**
 * What a version of a Devil Fruit Type says - the body of its {@link VersionResponse}.
 *
 * @param translations name and description per language code
 * @param subcategories in the order they are shown; empty for none
 */
public record DevilFruitTypeResponse(String romaji, Map<String, DevilFruitTypeTranslationResponse> translations,
		List<DevilFruitTypeSubcategoryResponse> subcategories) {

}
