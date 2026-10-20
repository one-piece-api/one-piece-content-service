package dev.onepieceapi.contentservice.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Where the reader first meets an item (ADR-0008), one part per medium, each optional.
 *
 * @param chapter the id of the content of the manga chapter; whether it can be chosen is
 * answered by {@code GET /chapters/linkable}; left out for none
 * @param episode the id of the content of the anime episode; whether it can be chosen is
 * answered by {@code GET /episodes/linkable}; left out for none
 */
public record FirstAppearanceRequest(
		@Schema(description = "The content id of the chapter; left out for none") UUID chapter,
		@Schema(description = "The content id of the episode; left out for none") UUID episode) {

}
