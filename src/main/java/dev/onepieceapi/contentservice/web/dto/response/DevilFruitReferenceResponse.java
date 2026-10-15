package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;
import java.util.UUID;

/**
 * A Devil Fruit as another content points to it: as of today, never what it said when the
 * pointing version was written.
 *
 * @param id the content id
 * @param romaji null when it has nothing approved to be called by
 * @param names the name per language code, for the languages that have one
 */
public record DevilFruitReferenceResponse(UUID id, String romaji, Map<String, String> names) {

}
