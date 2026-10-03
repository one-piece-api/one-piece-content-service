package dev.onepieceapi.contentservice.domain.dashboard;

import dev.onepieceapi.contentservice.domain.workflow.EntityType;

/**
 * The filters of a dashboard status page (UF-CNT-19), all optional and combined.
 *
 * @param entityType only contents of this kind; null for every kind
 * @param author only versions written by this username; null for anyone's
 * @param mine only the caller's share, as {@link MineScope} defines it for the status;
 * ignored for a status with no "mine"
 */
public record StatusFilter(EntityType entityType, String author, boolean mine) {

}
