package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.DomainException;

/**
 * Raised when a version is submitted saying exactly what another version of the same
 * content says (docs/user-flows/content-editorial-workflow.md 3.3): bringing old content
 * back is what restoring a version is for. Names that version under {@code identicalTo}.
 */
public class VersionIdenticalException extends DomainException {

	public VersionIdenticalException(int identicalTo) {
		super(ContentErrorCode.VERSION_IDENTICAL, "The version says exactly what version " + identicalTo + " says");
		withDetail("identicalTo", identicalTo);
	}

}
