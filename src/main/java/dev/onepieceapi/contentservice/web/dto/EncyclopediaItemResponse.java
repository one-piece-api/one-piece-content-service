package dev.onepieceapi.contentservice.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of "Enciclopedia" (Step 5) - an item currently `REVIEWED` (awaiting publish,
 * shown so a PUBLISHER knows what to act on) or `PUBLISHED`. `RETIRED` is a valid value
 * too (declared now so the column/contract never needs widening) but unreachable until
 * Step 8 adds Retire. Item-keyed, not working-revision-keyed, unlike
 * {@link WorkingRevisionSummaryResponse}/{@link ReviewQueueItemResponse}: once published,
 * there is no single working revision left representing "the" item.
 * {@code workingRevisionId} (non-null only for a `REVIEWED` row) is the id Publish (`POST
 * /devil-fruit-types/{workingRevisionId}/publish`) actually acts on - the item id alone
 * isn't enough to call it.
 */
public record EncyclopediaItemResponse(UUID itemId, UUID workingRevisionId, String entityType, String romaji,
		String displayName, String status, Instant updatedAt) {

}
