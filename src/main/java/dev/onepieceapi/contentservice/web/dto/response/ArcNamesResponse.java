package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a list row shows of an arc - the body of its {@link ContentSummaryResponse}:
 * enough to name it and say which saga it is of, without the descriptions.
 *
 * @param names the title per language code, for the languages that have one
 * @param saga the saga it belongs to, as it is today; null while not chosen
 */
public record ArcNamesResponse(Integer number, Map<String, String> names, NumberedReferenceResponse saga) {

}
