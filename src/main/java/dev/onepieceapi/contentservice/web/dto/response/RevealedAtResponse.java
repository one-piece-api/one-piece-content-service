package dev.onepieceapi.contentservice.web.dto.response;

/**
 * Where the reader learns of an event, as written (implementation plan of the timeline,
 * T5), one part per medium; only the manga part exists today (ADR-0008).
 *
 * @param chapter the chapter, as it is today
 */
public record RevealedAtResponse(NumberedReferenceResponse chapter) {

}
