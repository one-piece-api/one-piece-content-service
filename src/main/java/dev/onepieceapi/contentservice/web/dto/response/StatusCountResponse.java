package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;

/**
 * One tile of the dashboard.
 *
 * @param count how many contents have a version visible to the caller in this status
 * @param mine how many of them are the caller's - their drafts, the reviews they hold;
 * null for a status with no "mine"
 */
public record StatusCountResponse(VersionStatus status, long count, Long mine) {

}
