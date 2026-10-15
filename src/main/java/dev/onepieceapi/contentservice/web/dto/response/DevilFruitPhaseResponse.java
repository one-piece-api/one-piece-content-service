package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * One knowledge phase of a Devil Fruit (implementation plan of the timeline, T9;
 * ADR-0010): only the fields revealed at its chapter, each with the value believed
 * before; a field null is not part of it.
 *
 * @param supersededAt where the reader learns better
 * @param type the Devil Fruit Type believed, as it is today; null when not part of it
 * @param subcategory the subcategory believed, of the type in force at the phase
 * @param translations what was believed per language code
 */
public record DevilFruitPhaseResponse(SupersededAtResponse supersededAt, String romaji, TypeReferenceResponse type,
		SubcategoryReferenceResponse subcategory, Map<String, DevilFruitTranslationResponse> translations) {

}
