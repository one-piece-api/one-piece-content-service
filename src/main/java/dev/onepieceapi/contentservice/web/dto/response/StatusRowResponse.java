package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row of a dashboard status page: a content of any kind, by its most recent version
 * in the status.
 *
 * @param title how the version names its content
 * @param claimant the reviewer holding the version, if any
 * @param onlineVersionNumber the version of the content online now, which may be another
 * one; null when nothing is online
 * @param allowedActions what the caller may do with the version - the same as on its
 * detail screen
 * @param overrideActions those of the allowed actions the caller may perform only through
 * {@code content:admin}
 */
@Builder
public record StatusRowResponse(EntityType entityType, UUID contentId, int versionNumber, VersionStatus status,
		ContentTitleResponse title, UserResponse author, UserResponse claimant, Instant updatedAt,
		Integer onlineVersionNumber, List<VersionAction> allowedActions, List<VersionAction> overrideActions) {

}
