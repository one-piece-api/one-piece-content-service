package dev.onepieceapi.contentservice.web.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * {@code sequenceNumber}/{@code publisherEmail} are {@code null} for a `REVIEWED`
 * (not-yet-published) row - they only exist once a {@code ContentVersionEntity} does.
 */
public record EncyclopediaItemDetailResponse(UUID itemId, String romaji, String status,
		Map<String, TranslationResponse> translations, Instant updatedAt, Integer sequenceNumber,
		String publisherEmail) {

}
