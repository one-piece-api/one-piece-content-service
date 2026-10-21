package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a version of a Species says - the body of its {@link VersionResponse}.
 *
 * @param translations name and description per language code
 * @param image its picture; null for none
 * @param firstAppearance the chapter and/or the episode it first appears in; null for
 * none
 */
public record SpeciesResponse(String romaji, Map<String, SpeciesTranslationResponse> translations, ImageResponse image,
		FirstAppearanceResponse firstAppearance) {

}
