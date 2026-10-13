package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a version of an arc says - the body of its {@link VersionResponse}.
 *
 * @param saga the saga it belongs to, as it is today; null while not chosen
 * @param translations title and description per language code
 */
public record ArcResponse(Integer number, NumberedReferenceResponse saga,
		Map<String, StoryTranslationResponse> translations) {

}
