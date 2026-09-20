package dev.onepieceapi.contentservice.web.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WorkingRevisionDetailResponse(UUID id, UUID itemId, String romaji, String status,
		Map<String, TranslationResponse> translations, Instant updatedAt) {

}
