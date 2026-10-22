package dev.onepieceapi.contentservice.domain.affiliation;

import java.util.Map;
import java.util.UUID;

/**
 * An Affiliation as a character points to it (implementation plan of the Character, C8):
 * its content id, and what to call it and how to treat it as it was last approved - the
 * affiliation as of today, never a copy kept in the pointing version.
 *
 * @param names the name per language code, for the languages that have one
 * @param kind null for a version written before kinds existed: the editor needs it to
 * offer a rank
 */
public record AffiliationReference(UUID id, String romaji, Map<String, String> names, AffiliationKind kind) {

}
