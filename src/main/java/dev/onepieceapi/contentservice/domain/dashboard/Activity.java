package dev.onepieceapi.contentservice.domain.dashboard;

import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * One of the caller's own latest actions on a content, read from the audit log
 * (UF-CNT-19, implementation plan D4).
 *
 * @param action the stable audit action name, e.g. {@code VERSION_PUBLISHED}
 * @param versionNumber the version acted on; null when that version no longer exists - a
 * discarded draft
 * @param label what the content was called when the action happened
 * @param title what it is called now, from its most recent version the caller sees; null
 * when the caller sees none any more, or the content is gone - the action is then shown
 * by its label, without a link
 */
@Builder
public record Activity(String action, Instant occurredAt, EntityType entityType, UUID contentId, Integer versionNumber,
		String label, ContentTitle title) {

}
