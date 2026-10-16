package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * One reveal of a Devil Fruit (implementation plan of the timeline, T9; ADR-0010): only
 * the fields revealed at its chapter, each with the value believed before; a field null
 * is not part of it.
 *
 * @param revealedAt where the reader learns better
 * @param type the Devil Fruit Type believed, as it is today; null when not part of it
 * @param subcategory the subcategory believed, of the type in force at the reveal
 * @param translations what was believed per language code
 * @param titles the short title of the reveal per language code
 */
public record DevilFruitRevealResponse(RevealedAtResponse revealedAt, String romaji, TypeReferenceResponse type,
		SubcategoryReferenceResponse subcategory, Map<String, DevilFruitTranslationResponse> translations,
		Map<String, String> titles) {

}
