package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row of an entity list: a content, represented by one of its versions.
 *
 * @param <T> what the row shows of that version, e.g. a
 * {@link DevilFruitTypeNamesResponse}
 * @param id the id of the content
 * @param onlineVersionNumber the version currently online, possibly another one; null
 * when nothing is online
 * @param body what the row shows of the version
 * @param allowedActions what the caller may do with the version shown
 */
@Builder
public record ContentSummaryResponse<T>(UUID id, int versionNumber, VersionStatus status, UserResponse author,
		Instant updatedAt, Integer onlineVersionNumber, T body, List<VersionAction> allowedActions) {

}
