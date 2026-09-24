package dev.onepieceapi.contentservice.persistence.entity;

/**
 * A working revision's own lifecycle state
 * (docs/user-flows/authentication-and-user-management.md 4.1/4.2). {@link #SUPERSEDED} is
 * terminal: reached only when a *different* working revision of the same item is later
 * approved while this one is still {@code REVIEWED} (4.1's supersession rule) - it never
 * transitions anywhere from there, and its approval survives only in the audit log.
 * {@link #PUBLISHED} is likewise terminal: reached when this working revision is the one
 * a successful Publish (UF-CNT-07) consumes - "the item now rests with no working
 * revision in REVIEWED", so the candidate itself must leave that status, not just the
 * item's own derived PUBLISHED/RETIRED state (Step 5). Deliberately has no
 * {@code REJECTED} value: a reject (UF-CNT-06) always lands back on {@code DRAFT},
 * editable again by its author - the same state a never-submitted draft is in. A rejected
 * draft is instead recognized by {@code DRAFT} plus a non-null {@code rejectionReason} on
 * the entity; a second status value for the same editable state would only duplicate
 * every `DRAFT` transition rule for no behavioral difference.
 */
public enum WorkingRevisionStatus {

	DRAFT, IN_REVIEW, REVIEWED, SUPERSEDED, PUBLISHED

}
