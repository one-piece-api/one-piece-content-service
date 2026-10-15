package dev.onepieceapi.contentservice.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Where the reader learns better than a knowledge phase (implementation plan of the
 * timeline, T9), one part per medium; only the manga part exists today (ADR-0008).
 *
 * @param chapter the id of the content of the chapter; whether it can be chosen is
 * answered by {@code GET /chapters/linkable}
 */
public record SupersededAtRequest(@Schema(description = "The content id of the chapter") UUID chapter) {

}
