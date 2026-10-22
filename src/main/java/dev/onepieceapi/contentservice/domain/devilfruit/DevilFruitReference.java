package dev.onepieceapi.contentservice.domain.devilfruit;

import java.util.Map;
import java.util.UUID;

/**
 * A Devil Fruit as a character points to it (implementation plan of the Character, C10):
 * its content id, and what to call it and how to treat it as it was last approved - the
 * fruit as of today, never a copy kept in the pointing version.
 *
 * @param names the name per language code, for the languages that have one
 * @param multipleUsers whether several characters may use it at the same time
 */
public record DevilFruitReference(UUID id, String romaji, Map<String, String> names, boolean multipleUsers) {

}
