package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * One version: its workflow, the same for every kind of content, and its body.
 *
 * @param <T> what a version of this kind of content says, e.g. a
 * {@link DevilFruitTypeResponse}
 * @param rejectionReason why the last review failed: from the rejection until the version
 * is submitted again
 * @param body what this version says
 * @param allowedActions what the caller may do with this version, decided by the same
 * rules that guard the endpoints - the client offers these and nothing else
 * @param overrideActions those of the allowed actions the caller may perform only through
 * {@code content:admin}, on someone else's version or claim
 */
@Builder
public record VersionResponse<T>(int number, VersionStatus status, UserResponse author, Integer basedOn,
		UserResponse claimant, boolean everPublished, String rejectionReason, T body,
		List<VersionAction> allowedActions, List<VersionAction> overrideActions, Instant createdAt, Instant updatedAt) {

}
