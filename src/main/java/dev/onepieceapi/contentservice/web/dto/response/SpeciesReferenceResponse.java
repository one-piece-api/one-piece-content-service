package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;
import java.util.UUID;

/**
 * A Species as a character may point to it: the species as of today, never what it said
 * when it was written.
 *
 * @param id the content id of the species
 * @param romaji null when the species has nothing approved to call it by
 * @param names the name per language code, for the languages that have one
 */
public record SpeciesReferenceResponse(UUID id, String romaji, Map<String, String> names) {

}
