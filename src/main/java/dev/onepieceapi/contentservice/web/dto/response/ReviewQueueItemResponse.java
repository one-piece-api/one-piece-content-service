package dev.onepieceapi.contentservice.web.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the shared "In Revisione" queue (docs/implementation-plan-content.md Step 3)
 * - unlike {@link WorkingRevisionSummaryResponse}'s "Le mie bozze" (always the caller's
 * own), this list spans every author, so {@code authorEmail} is meaningful here and
 * {@code claimedByEmail} (nullable) shows every REVIEWER whether it's free or, if not,
 * who is currently accountable for it.
 */
public record ReviewQueueItemResponse(UUID id, UUID itemId, String entityType, String romaji, String displayName,
		String authorEmail, String claimedByEmail, Instant updatedAt) {

}
