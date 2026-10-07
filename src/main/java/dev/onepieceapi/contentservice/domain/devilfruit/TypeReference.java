package dev.onepieceapi.contentservice.domain.devilfruit;

import java.util.Map;
import java.util.UUID;

/**
 * A Devil Fruit Type as another content points to it (implementation plan of the Devil
 * Fruit, D1, D4): its content id, and what to call it as it was last approved - the type
 * as of today, never a copy kept in the pointing version.
 *
 * @param names the name per language code, for the languages that have one
 */
public record TypeReference(UUID id, String romaji, Map<String, String> names) {

}
