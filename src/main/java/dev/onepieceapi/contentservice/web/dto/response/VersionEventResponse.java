package dev.onepieceapi.contentservice.web.dto.response;

import java.time.Instant;

/**
 * One step of the workflow timeline of a version.
 *
 * @param action the audit action name - a stable contract, the client maps it to a label
 * @param detail what the action carried with it, e.g. a rejection reason
 */
public record VersionEventResponse(String action, UserResponse actor, String detail, Instant occurredAt) {

}
