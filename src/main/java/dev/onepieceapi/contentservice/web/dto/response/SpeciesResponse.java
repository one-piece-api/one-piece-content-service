package dev.onepieceapi.contentservice.web.dto.response;

import java.util.List;
import java.util.Map;

/**
 * What a version of a Species says - the body of its {@link VersionResponse}.
 *
 * @param translations the name per language code
 * @param image its picture; null for none
 * @param firstAppearance the chapter and/or the episode it first appears in; null for
 * none
 * @param description its paragraphs, in order; empty for none
 */
public record SpeciesResponse(String romaji, Map<String, SpeciesTranslationResponse> translations, ImageResponse image,
		FirstAppearanceResponse firstAppearance, List<ParagraphResponse> description) {

}
