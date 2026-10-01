package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import lombok.Builder;

import java.time.Instant;

/**
 * One link of the version chain of a content: its workflow, without what it says. The
 * same for every kind of content.
 *
 * @param basedOn the version it was opened from; null for the first one
 * @param claimant the reviewer holding it, if any
 * @param everPublished whether it has been online at some point
 */
@Builder
public record VersionSummaryResponse(int number, VersionStatus status, UserResponse author, Integer basedOn,
		UserResponse claimant, boolean everPublished, Instant createdAt, Instant updatedAt) {

}
