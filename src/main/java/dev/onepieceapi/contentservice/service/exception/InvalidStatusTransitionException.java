package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.contentservice.persistence.entity.WorkingRevisionStatus;
import dev.onepieceapi.exception.ConflictException;

/**
 * Raised when an action is attempted on a working revision that isn't in the status it
 * requires (e.g. submitting one that isn't {@code DRAFT}, withdrawing one that isn't
 * {@code IN_REVIEW}) - generic across every state-transition action this service exposes,
 * not just Step 2's.
 */
public class InvalidStatusTransitionException extends ConflictException {

	public InvalidStatusTransitionException(WorkingRevisionStatus current, String action) {
		super(ContentErrorCode.INVALID_STATUS_TRANSITION,
				"Cannot " + action + " a working revision in status " + current);
	}

}
