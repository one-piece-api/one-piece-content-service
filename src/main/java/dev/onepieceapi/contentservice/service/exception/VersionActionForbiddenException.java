package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.exception.ForbiddenException;

/**
 * Raised when the caller sees a version but the action is not theirs to perform on it -
 * e.g. editing a draft someone else wrote.
 */
public class VersionActionForbiddenException extends ForbiddenException {

	public VersionActionForbiddenException(VersionAction action) {
		super(ContentErrorCode.VERSION_ACTION_FORBIDDEN, "The caller may not " + action + " this version");
	}

}
