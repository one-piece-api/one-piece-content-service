package dev.onepieceapi.contentservice.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Where the reader learns of an event (implementation plan of the timeline, T5) or a
 * reveal of an item takes place (T9, T12), one part per medium; only the manga part
 * exists today (ADR-0008).
 *
 * @param chapter the id of the content of the chapter; whether it can be chosen is
 * answered by {@code GET /chapters/linkable}; for an event, left out for the chapter it
 * occurs in
 */
public record RevealedAtRequest(@Schema(
		description = "The content id of the chapter; for an event, left out for the chapter it occurs in") UUID chapter) {

}
