package dev.onepieceapi.contentservice.web.dto.response;

/**
 * Where the reader first meets an item (ADR-0008), one part per medium.
 *
 * @param chapter the manga chapter, as it is today; null for none
 * @param episode the anime episode, as it is today; null for none
 */
public record FirstAppearanceResponse(NumberedReferenceResponse chapter, NumberedReferenceResponse episode) {

}
