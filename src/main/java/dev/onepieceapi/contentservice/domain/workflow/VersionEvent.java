package dev.onepieceapi.contentservice.domain.workflow;

import dev.onepieceapi.contentservice.domain.security.User;

import java.time.Instant;

/**
 * One step in the history of a version, read from the audit log
 * (docs/user-flows/content-editorial-workflow.md 7): who did what and when.
 *
 * @param detail what the action carried with it, e.g. a rejection reason
 */
public record VersionEvent(String action, User actor, String detail, Instant occurredAt) {

}
