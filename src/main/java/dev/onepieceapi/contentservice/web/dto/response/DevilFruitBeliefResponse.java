package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What the reader knows of a Devil Fruit at its first appearance, for the fields a reveal
 * changes later (implementation plan of the timeline, T13); a field null is not part of
 * it.
 *
 * @param type a Devil Fruit Type, as it is today; null when not part of it
 * @param subcategory a subcategory of that type
 * @param translations the translated values per language code
 */
public record DevilFruitBeliefResponse(String romaji, TypeReferenceResponse type,
		SubcategoryReferenceResponse subcategory, Map<String, DevilFruitTranslationResponse> translations) {

}
