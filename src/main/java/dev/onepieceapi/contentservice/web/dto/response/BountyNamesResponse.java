package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import dev.onepieceapi.contentservice.domain.bounty.BountyValidity;

/**
 * What a list row shows of a bounty - the body of its {@link ContentSummaryResponse}:
 * enough to name it and say whether it stands, without the texts. A bounty has no name:
 * its key is the poster's name and the amount together.
 *
 * @param key the key text; null while the poster's name or the amount is missing
 * @param validity derived, never stored: as the caller sees the other bounties
 */
public record BountyNamesResponse(String key, String posterName, Long amount, BountyCondition condition,
		BountyValidity validity) {

}
