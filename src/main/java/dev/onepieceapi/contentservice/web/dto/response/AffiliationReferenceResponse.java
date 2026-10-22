package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;

import java.util.Map;
import java.util.UUID;

/**
 * An Affiliation as a character may point to it: the affiliation as of today, never what
 * it said when it was written.
 *
 * @param id the content id of the affiliation
 * @param romaji null when the affiliation has nothing approved to call it by
 * @param names the name per language code, for the languages that have one
 * @param kind null for a version written before kinds existed, and when nothing is
 * approved
 */
public record AffiliationReferenceResponse(UUID id, String romaji, Map<String, String> names, AffiliationKind kind) {

}
