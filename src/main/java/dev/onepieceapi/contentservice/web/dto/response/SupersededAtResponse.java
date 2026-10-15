package dev.onepieceapi.contentservice.web.dto.response;

/**
 * Where the reader learns better than a knowledge phase (implementation plan of the
 * timeline, T9), one part per medium; only the manga part exists today (ADR-0008).
 *
 * @param chapter the chapter, as it is today; null while not chosen
 */
public record SupersededAtResponse(NumberedReferenceResponse chapter) {

}
