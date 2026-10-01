package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import lombok.Builder;

import java.time.Instant;

/**
 * One version: its workflow, the same for every kind of content, and its body.
 *
 * @param <T> what a version of this kind of content says, e.g. a
 * {@link DevilFruitTypeResponse}
 * @param rejectionReason why the last review failed, while rejected
 * @param body what this version says
 */
@Builder
public record VersionResponse<T>(int number, VersionStatus status, UserResponse author, Integer basedOn,
		UserResponse claimant, boolean everPublished, String rejectionReason, T body, Instant createdAt,
		Instant updatedAt) {

}
