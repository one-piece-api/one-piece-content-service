package dev.onepieceapi.contentservice.domain.story;

import java.util.Map;
import java.util.UUID;

/**
 * A content keyed by a number as another content points to it - the saga of an arc: its
 * content id, its number and its titles as it was last approved, never a copy kept in the
 * pointing version.
 *
 * @param number null when it has nothing approved to be called by
 * @param names the title per language code, for the languages that have one
 */
public record NumberedReference(UUID id, Integer number, Map<String, String> names) {

}
