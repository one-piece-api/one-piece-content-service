package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a version of a Devil Fruit says - the body of its {@link VersionResponse}.
 *
 * @param type the Devil Fruit Type it belongs to, as it is today; null while not chosen
 * @param translations name, description, advantages and disadvantages per language code
 * @param image its image; null for none
 */
public record DevilFruitResponse(String romaji, TypeReferenceResponse type,
		Map<String, DevilFruitTranslationResponse> translations, ImageResponse image) {

}
