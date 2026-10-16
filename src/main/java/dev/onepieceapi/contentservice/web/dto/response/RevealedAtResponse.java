package dev.onepieceapi.contentservice.web.dto.response;

/**
 * Where the reader learns of an event (implementation plan of the timeline, T5) or a
 * reveal of an item takes place (T9, T12), as written, one part per medium; only the
 * manga part exists today (ADR-0008).
 *
 * @param chapter the chapter, as it is today; null for a reveal while not chosen
 */
public record RevealedAtResponse(NumberedReferenceResponse chapter) {

}
