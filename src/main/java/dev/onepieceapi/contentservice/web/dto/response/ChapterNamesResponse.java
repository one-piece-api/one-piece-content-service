package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a list row shows of a chapter - the body of its {@link ContentSummaryResponse}:
 * enough to name it and say where it belongs, without the descriptions.
 *
 * @param names the title per language code, for the languages that have one
 * @param arc the arc it belongs to, as it is today; null while not chosen
 * @param volume the volume that collects it, as it is today; null for none
 */
public record ChapterNamesResponse(Integer number, Map<String, String> names, NumberedReferenceResponse arc,
		NumberedReferenceResponse volume) {

}
