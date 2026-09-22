package dev.onepieceapi.contentservice.persistence;

/**
 * A working revision's own lifecycle state
 * (docs/user-flows/authentication-and-user-management.md 4.1/4.2). {@link #SUPERSEDED} is
 * terminal: reached only when a *different* working revision of the same item is later
 * approved while this one is still {@code REVIEWED} (4.1's supersession rule) - it never
 * transitions anywhere from there, and its approval survives only in the audit log.
 * Deliberately has no {@code REJECTED} value: a reject (UF-CNT-06) always lands back on
 * {@code DRAFT}, editable again by its author - the same state a never-submitted draft is
 * in. A rejected draft is instead recognized by {@code DRAFT} plus a non-null
 * {@code rejectionReason} on the entity; a second status value for the same editable
 * state would only duplicate every `DRAFT` transition rule for no behavioral difference.
 */
public enum WorkingRevisionStatus {

	DRAFT, IN_REVIEW, REVIEWED, SUPERSEDED

}
