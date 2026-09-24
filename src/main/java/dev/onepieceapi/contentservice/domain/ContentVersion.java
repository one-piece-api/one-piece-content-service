package dev.onepieceapi.contentservice.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One immutable published snapshot, fully assembled - see {@link WorkingRevision} for why
 * translations are always embedded rather than fetched separately.
 */
public record ContentVersion(UUID id, UUID itemId, int sequenceNumber, String romaji, UUID publisherId,
		String publisherEmail, Instant publishedAt, Map<String, Translation> translations) {

}
