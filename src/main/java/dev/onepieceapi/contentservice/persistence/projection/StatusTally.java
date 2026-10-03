package dev.onepieceapi.contentservice.persistence.projection;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;

/**
 * How many contents have a version in a status - see {@code ContentVersionRepository}.
 */
public record StatusTally(VersionStatus status, long contents) {

}
