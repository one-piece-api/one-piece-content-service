package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.exception.web.FieldViolation;

import java.util.List;

/**
 * Raised when a version is submitted with a required field still empty in some language
 * of the catalog (docs/user-flows/content-editorial-workflow.md 3.2, 3.3). Names every
 * missing field under {@code errors}, the same shape a failed request validation answers
 * with.
 */
public class VersionIncompleteException extends DomainException {

	public VersionIncompleteException(List<FieldViolation> missing) {
		super(ContentErrorCode.VERSION_INCOMPLETE, "The version is not complete enough to be submitted");
		withDetail("errors", missing);
	}

}
