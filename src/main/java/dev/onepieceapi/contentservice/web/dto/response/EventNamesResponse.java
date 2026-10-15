package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a list row shows of an event - the body of its {@link ContentSummaryResponse}:
 * enough to name it and say when it happens, without the descriptions.
 *
 * @param names the title per language code, for the languages that have one
 * @param occursAt when it happens; null while not written
 */
public record EventNamesResponse(Map<String, String> names, EventTimeResponse occursAt) {

}
