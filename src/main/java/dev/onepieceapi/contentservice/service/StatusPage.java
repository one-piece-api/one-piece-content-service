package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.dashboard.StatusRow;

import org.springframework.data.domain.Page;

/**
 * One page of a dashboard status page, with the two counters of its scope switch - the
 * whole status, whatever page and filter is on, like the home's tile.
 *
 * @param all how many contents are in the status
 * @param mine how many of them are the caller's; null for a status with no "mine"
 */
public record StatusPage(Page<StatusRow> rows, long all, Long mine) {

}
