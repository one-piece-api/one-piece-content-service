package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;
import java.util.UUID;

/**
 * A Devil Fruit Type as a fruit points to it: the type as of today, never what it said
 * when the fruit was written.
 *
 * @param id the content id of the type
 * @param romaji null when the type has nothing approved to call it by
 * @param names the name per language code, for the languages that have one
 */
public record TypeReferenceResponse(UUID id, String romaji, Map<String, String> names) {

}
