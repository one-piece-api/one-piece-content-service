package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.character.MarineRank;

import java.util.Map;

/**
 * One stay of a Character in an affiliation.
 *
 * @param discovered where the reader learns it; null while not chosen
 * @param ended where the reader learns it is over; null for none
 * @param role per language code, for the languages it is written in
 * @param marineRank null for none
 */
public record MembershipPeriodResponse(FirstAppearanceResponse discovered, FirstAppearanceResponse ended,
		Map<String, String> role, MarineRank marineRank) {

}
