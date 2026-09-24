package dev.onepieceapi.contentservice.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A working revision's business state, fully assembled - unlike
 * {@code persistence.entity.WorkingRevisionEntity}, which only maps one database row,
 * this always carries its own translations, so a caller never has to remember to fetch
 * them separately alongside it.
 */
public record WorkingRevision(UUID id, UUID itemId, UUID authorId, String authorEmail, String romaji,
		WorkingRevisionStatus status, Map<String, Translation> translations, UUID claimedBy, String claimedByEmail,
		String rejectionReason, Instant updatedAt) {

}
