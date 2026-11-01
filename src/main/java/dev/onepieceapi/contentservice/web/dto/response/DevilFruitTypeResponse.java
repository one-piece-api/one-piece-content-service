package dev.onepieceapi.contentservice.web.dto.response;

import java.util.List;
import java.util.Map;

/**
 * What a version of a Devil Fruit Type says - the body of its {@link VersionResponse}.
 *
 * @param translations the name per language code
 * @param subcategories in the order they are shown; empty for none
 * @param firstAppearance the chapter the reader first meets it in; null for none
 * @param description its paragraphs, in order; empty for none
 * @param advantages as {@code description}
 * @param disadvantages as {@code description}
 */
public record DevilFruitTypeResponse(String romaji, Map<String, DevilFruitTypeTranslationResponse> translations,
		List<DevilFruitTypeSubcategoryResponse> subcategories, FirstAppearanceResponse firstAppearance,
		List<ParagraphResponse> description, List<ParagraphResponse> advantages,
		List<ParagraphResponse> disadvantages) {

}
