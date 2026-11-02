package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What the reader believes of a Devil Fruit at its first appearance (workflow 3.7.1); a
 * value not part of it is null.
 *
 * @param translations the name per language code
 */
public record DevilFruitKnowledgeResponse(String romaji, TypeReferenceResponse type,
		SubcategoryReferenceResponse subcategory, Map<String, DevilFruitTranslationResponse> translations) {

}
