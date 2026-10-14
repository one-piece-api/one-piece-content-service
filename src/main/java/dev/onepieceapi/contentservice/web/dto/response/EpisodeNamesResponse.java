package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a list row shows of an episode - the body of its {@link ContentSummaryResponse}:
 * enough to name it and say which arc it is of, without the descriptions.
 *
 * @param names the title per language code, for the languages that have one
 * @param arc the arc it belongs to, as it is today; null for none
 */
public record EpisodeNamesResponse(Integer number, Map<String, String> names, NumberedReferenceResponse arc) {

}
