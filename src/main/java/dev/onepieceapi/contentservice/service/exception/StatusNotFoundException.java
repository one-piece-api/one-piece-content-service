package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.exception.NotFoundException;

/**
 * Raised when a dashboard status page does not exist for the caller: a status they may
 * not see, or one with no page - as for a version, not seeing it and it not existing are
 * indistinguishable on purpose.
 */
public class StatusNotFoundException extends NotFoundException {

	public StatusNotFoundException(VersionStatus status) {
		super(ContentErrorCode.STATUS_NOT_FOUND, "No dashboard page for status " + status);
	}

}
