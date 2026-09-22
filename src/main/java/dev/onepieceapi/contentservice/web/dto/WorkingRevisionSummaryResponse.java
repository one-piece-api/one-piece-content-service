package dev.onepieceapi.contentservice.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of "Le mie bozze" - {@code entityType} is a constant today ({@code
 * DEVIL_FRUIT_TYPE}) but present from the start so a second content entity is an
 * additional source to query, not a response-shape change
 * (docs/implementation-plan-content.md 2). {@code rejectionReason} (nullable) lets the
 * list distinguish a rejected `DRAFT` from an ordinary one without a separate status
 * value - see {@code WorkingRevisionStatus}'s javadoc for why rejection doesn't get its
 * own status.
 */
public record WorkingRevisionSummaryResponse(UUID id, UUID itemId, String entityType, String romaji, String displayName,
		String status, Instant updatedAt, String rejectionReason) {

}
