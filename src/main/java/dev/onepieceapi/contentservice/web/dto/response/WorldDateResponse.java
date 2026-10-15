package dev.onepieceapi.contentservice.web.dto.response;

/**
 * A year, or a span of years, of the One Piece calendar, before the story starts
 * (implementation plan of the timeline, T4).
 *
 * @param yearTo the last year; null for one year
 */
public record WorldDateResponse(Integer yearFrom, Integer yearTo, boolean approximate) {

}
