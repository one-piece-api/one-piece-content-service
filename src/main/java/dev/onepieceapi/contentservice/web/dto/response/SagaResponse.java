package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a version of a saga says - the body of its {@link VersionResponse}.
 *
 * @param translations title and description per language code
 */
public record SagaResponse(Integer number, Map<String, StoryTranslationResponse> translations) {

}
