package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import dev.onepieceapi.contentservice.domain.bounty.BountyValidity;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * The filters of the list of bounties, as query parameters: those of every entity list -
 * see {@link ContentListRequest} - and the three of its own. All optional and combinable.
 *
 * @param q text contained in the key: the poster's name, then the amount
 * @param validity only the bounties whose version shown is this valid, as the caller sees
 * the other bounties
 * @param condition only the bounties with this condition
 * @param holder only the bounties of this character, by its content id
 */
public record BountyListRequest(VersionStatus status, String q, String author,
		@PositiveOrZero Integer updatedWithinDays, BountyValidity validity, BountyCondition condition, UUID holder) {

}
