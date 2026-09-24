package dev.onepieceapi.contentservice.web.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of "Enciclopedia" (Step 5, extended Step 8) - an item currently `REVIEWED`
 * (awaiting publish, shown so a PUBLISHER knows what to act on), `PUBLISHED`, or
 * `RETIRED` (shown with the content of its last live version). Item-keyed, not
 * working-revision-keyed, unlike
 * {@link WorkingRevisionSummaryResponse}/{@link ReviewQueueItemResponse}: once published,
 * there is no single working revision left representing "the" item.
 * {@code workingRevisionId} (non-null only for a `REVIEWED` row) is the id Publish (`POST
 * /devil-fruit-types/{workingRevisionId}/publish`) actually acts on - the item id alone
 * isn't enough to call it.
 */
public record EncyclopediaItemResponse(UUID itemId, UUID workingRevisionId, String entityType, String romaji,
		String displayName, String status, Instant updatedAt) {

}
