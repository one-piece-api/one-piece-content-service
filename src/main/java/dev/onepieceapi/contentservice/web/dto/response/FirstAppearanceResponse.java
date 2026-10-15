package dev.onepieceapi.contentservice.web.dto.response;

/**
 * Where the reader first meets an item (implementation plan of the timeline, T7), one
 * part per medium; only the manga part exists today (ADR-0008).
 *
 * @param chapter the chapter, as it is today
 */
public record FirstAppearanceResponse(NumberedReferenceResponse chapter) {

}
