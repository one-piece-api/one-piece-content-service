package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a version of a Devil Fruit Type says - the body of its {@link VersionResponse}.
 *
 * @param translations name and description per language code
 */
public record DevilFruitTypeResponse(String romaji, Map<String, DevilFruitTypeTranslationResponse> translations) {

}
