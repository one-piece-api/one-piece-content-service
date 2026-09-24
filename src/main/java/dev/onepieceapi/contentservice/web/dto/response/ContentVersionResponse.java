package dev.onepieceapi.contentservice.web.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of Step 7's "Storico versioni": a published snapshot, which of them is
 * currently live, who published it and when. No content fields (romaji/translations) -
 * the panel is a list to pick a rollback target from, not a diff viewer.
 */
public record ContentVersionResponse(UUID id, int sequenceNumber, String publisherEmail, Instant publishedAt,
		boolean live) {

}
