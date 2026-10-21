package dev.onepieceapi.contentservice.domain.species;

import java.util.Map;
import java.util.UUID;

/**
 * A Species as another content points to it (implementation plan of the Species, D7): its
 * content id, and what to call it as it was last approved - the species as of today,
 * never a copy kept in the pointing version.
 *
 * @param names the name per language code, for the languages that have one
 */
public record SpeciesReference(UUID id, String romaji, Map<String, String> names) {

}
