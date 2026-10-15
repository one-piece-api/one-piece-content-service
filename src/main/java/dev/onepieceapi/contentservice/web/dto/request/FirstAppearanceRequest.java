package dev.onepieceapi.contentservice.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Where the reader first meets an item (implementation plan of the timeline, T7), one
 * part per medium; only the manga part exists today (ADR-0008).
 *
 * @param chapter the id of the content of the chapter; whether it can be chosen is
 * answered by {@code GET /chapters/linkable}; left out for none
 */
public record FirstAppearanceRequest(
		@Schema(description = "The content id of the chapter; left out for none") UUID chapter) {

}
