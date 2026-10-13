package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a list row shows of a saga - the body of its {@link ContentSummaryResponse}:
 * enough to name it, without the descriptions.
 *
 * @param names the title per language code, for the languages that have one
 */
public record SagaNamesResponse(Integer number, Map<String, String> names) {

}
