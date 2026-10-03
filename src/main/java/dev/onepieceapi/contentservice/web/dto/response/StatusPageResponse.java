package dev.onepieceapi.contentservice.web.dto.response;

/**
 * One page of a dashboard status page, with the counters of its scope switch.
 *
 * @param all how many contents are in the status, whatever page and filter is on
 * @param mine how many of them are the caller's; null for a status with no "mine"
 */
public record StatusPageResponse(PageResponse<StatusRowResponse> rows, long all, Long mine) {

}
