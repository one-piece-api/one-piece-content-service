package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.security.User;
import lombok.Builder;

import java.util.UUID;

/**
 * What {@link AuditLogService} writes about one version of a content - what the history
 * of that version is read from.
 *
 * @param label what the version was called when the action happened
 * @param detail what the action carries with it, e.g. the reason of a rejection
 * @param override whether the actor could act only through {@code content:admin}
 */
@Builder
public record VersionAuditRecord(String action, User actor, UUID contentId, UUID versionId, String label, String detail,
		boolean override) {

}
