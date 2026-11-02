package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitField;

import java.util.Map;
import java.util.Set;

/**
 * A reveal of a Devil Fruit (workflow 3.7.1): a field named with no value reveals the
 * current one.
 *
 * @param titles the title per language code
 * @param translations the name learned per language code
 */
public record DevilFruitRevealResponse(FirstAppearanceResponse revealedAt, Map<String, String> titles,
		Set<DevilFruitField> fields, String romaji, TypeReferenceResponse type,
		SubcategoryReferenceResponse subcategory, Map<String, DevilFruitTranslationResponse> translations) {

}
