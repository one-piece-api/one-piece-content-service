package dev.onepieceapi.contentservice.web.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * {@code authorEmail}/{@code claimedByEmail} (nullable) let both "Le mie bozze" and the
 * review queue's own detail view (Step 3) share one shape rather than near-duplicating it
 * - harmless when unused (an author already knows they're the author).
 * {@code rejectionReason} is the most recent rejection's reason, visible to the author
 * until overwritten by a later one.
 */
public record WorkingRevisionDetailResponse(UUID id, UUID itemId, String romaji, String status,
		Map<String, TranslationResponse> translations, Instant updatedAt, String authorEmail, String claimedByEmail,
		String rejectionReason) {

}
