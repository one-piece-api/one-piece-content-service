package dev.onepieceapi.contentservice.web.dto.response;

import java.util.List;
import java.util.Map;

/**
 * What a version of a Devil Fruit says - the body of its {@link VersionResponse}.
 *
 * @param type the Devil Fruit Type it belongs to, as it is today; null while not chosen
 * @param subcategory the subcategory of that type it names; null for none
 * @param translations name, description, advantages and disadvantages per language code
 * @param image its image; null for none
 * @param firstAppearance the chapter the reader first meets it in; null for none
 * @param phases what the reader believed before each reveal, in the order of the story;
 * empty for none
 */
public record DevilFruitResponse(String romaji, TypeReferenceResponse type, SubcategoryReferenceResponse subcategory,
		Map<String, DevilFruitTranslationResponse> translations, ImageResponse image,
		FirstAppearanceResponse firstAppearance, List<DevilFruitPhaseResponse> phases) {

}
