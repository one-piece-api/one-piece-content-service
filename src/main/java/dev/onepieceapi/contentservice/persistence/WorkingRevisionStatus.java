package dev.onepieceapi.contentservice.persistence;

/**
 * A working revision's own lifecycle state
 * (docs/user-flows/authentication-and-user-management.md 4.1/4.2). {@link #SUPERSEDED} is
 * terminal: reached only when a *different* working revision of the same item is later
 * approved while this one is still {@code REVIEWED} (4.1's supersession rule) - it never
 * transitions anywhere from there, and its approval survives only in the audit log.
 */
public enum WorkingRevisionStatus {

	DRAFT, IN_REVIEW, REVIEWED, SUPERSEDED

}
