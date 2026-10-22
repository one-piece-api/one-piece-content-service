package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.character.MarineRank;

import java.util.Map;

/**
 * A membership of a Character.
 *
 * @param affiliation the affiliation as of today, with its kind
 * @param role per language code, for the languages it is written in
 * @param marineRank null for none
 */
public record MembershipResponse(AffiliationReferenceResponse affiliation, Map<String, String> role,
		MarineRank marineRank, boolean former) {

}
