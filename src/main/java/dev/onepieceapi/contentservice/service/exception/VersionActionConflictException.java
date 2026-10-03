package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.exception.ConflictException;

/**
 * Raised when an action is asked of a version that is not in a state for it, whoever asks
 * - e.g. editing a version that is no longer a draft.
 */
public class VersionActionConflictException extends ConflictException {

	public VersionActionConflictException(VersionAction action) {
		super(ContentErrorCode.VERSION_ACTION_CONFLICT, "This version is not in a state to " + action);
	}

}
