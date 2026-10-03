package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * One of the caller's own latest actions on a content.
 *
 * @param action the audit action name - a stable contract, the client maps it to a verb
 * @param entityType null when the content no longer exists
 * @param versionNumber null when the version acted on no longer exists - a discarded
 * draft
 * @param label what the content was called when the action happened
 * @param title what it is called now; null when the caller no longer sees it, or it is
 * gone - shown by its label, without a link
 */
@Builder
public record ActivityResponse(String action, Instant occurredAt, EntityType entityType, UUID contentId,
		Integer versionNumber, String label, ContentTitleResponse title) {

}
