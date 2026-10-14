package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a list row shows of a volume - the body of its {@link ContentSummaryResponse}:
 * enough to name it, with its cover.
 *
 * @param names the title per language code, for the languages that have one
 * @param cover its cover; null for none
 */
public record VolumeNamesResponse(Integer number, Map<String, String> names, ImageResponse cover) {

}
