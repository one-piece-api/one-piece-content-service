package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.MarineRank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * One stay of a Character in an affiliation, as a draft is saved (workflow 3.4.7): Usopp
 * has two in the Straw Hat Pirates, Koby one per rank.
 *
 * @param discovered as {@link WindowedRequest#discovered()}
 * @param ended where the reader learns the stay is over; left out for none, which only
 * the last period may be
 * @param role per language code; a language left out, or blank, is not written yet
 * @param marineRank allowed only in a Navy affiliation; left out for none
 */
public record MembershipPeriodRequest(FirstAppearanceRequest discovered, FirstAppearanceRequest ended,
		Map<String, @Size(max = Character.ROLE_MAX_LENGTH) String> role,
		MarineRank marineRank) implements WindowedRequest {

}
