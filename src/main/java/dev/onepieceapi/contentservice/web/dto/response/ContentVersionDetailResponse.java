package dev.onepieceapi.contentservice.web.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One version's full content (user-reported gap: the list-only
 * {@link ContentVersionResponse} gave a PUBLISHER no way to see what a past snapshot
 * actually said before deciding whether to restore it).
 */
public record ContentVersionDetailResponse(UUID id, int sequenceNumber, String publisherEmail, Instant publishedAt,
		boolean live, String romaji, Map<String, TranslationResponse> translations) {

}
