package dev.onepieceapi.contentservice.persistence;

/**
 * A working revision's own lifecycle state
 * (docs/user-flows/authentication-and-user-management.md 4.1/4.2). Only {@link #DRAFT} is
 * reachable through Step 1's endpoints - the full set is declared now so the column never
 * needs widening later.
 */
public enum WorkingRevisionStatus {

	DRAFT, IN_REVIEW, REVIEWED

}
