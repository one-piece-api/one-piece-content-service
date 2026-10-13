package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;
import java.util.UUID;

/**
 * A content keyed by a number as another content points to it - the saga of an arc: as of
 * today, never what it said when the pointing version was written. Its natural key is
 * carried under its own name, as {@code romaji} is in a Devil Fruit Type reference.
 *
 * @param id the content id
 * @param number null when it has nothing approved to be called by
 * @param names the title per language code, for the languages that have one
 */
public record NumberedReferenceResponse(UUID id, Integer number, Map<String, String> names) {

}
