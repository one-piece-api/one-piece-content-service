package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.web.dto.request.RevealedField;

import java.util.List;
import java.util.Map;

/**
 * One reveal of a Devil Fruit (implementation plan of the timeline, T13; ADR-0010): the
 * fields the reader learns at its chapter and, for those not yet the truth, the value
 * learned; a value null is not part of it.
 *
 * @param revealedAt where the reader learns
 * @param titles the short title of the reveal per language code
 * @param fields the fields learned there, in a fixed order
 * @param type the Devil Fruit Type learned, as it is today; null when not part of it
 * @param subcategory a subcategory of that type
 * @param translations the translated values learned per language code
 */
public record DevilFruitRevealResponse(RevealedAtResponse revealedAt, Map<String, String> titles,
		List<RevealedField> fields, String romaji, TypeReferenceResponse type, SubcategoryReferenceResponse subcategory,
		Map<String, DevilFruitTranslationResponse> translations) {

}
