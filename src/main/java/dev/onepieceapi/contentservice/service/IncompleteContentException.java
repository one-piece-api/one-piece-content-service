package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.exception.ValidationException;
import dev.onepieceapi.exception.web.FieldViolation;

import java.util.List;

/**
 * Raised by {@code submitForReview} (UF-CNT-03,
 * docs/user-flows/authentication-and-user-management.md 3.1/3.3) when a required field is
 * missing or over its length limit for at least one active language - a draft may be
 * saved incomplete/over-length, but not submitted that way.
 */
public class IncompleteContentException extends ValidationException {

	public IncompleteContentException(List<FieldViolation> violations) {
		super(ContentErrorCode.INCOMPLETE, "Working revision is not complete enough to submit for review");
		this.withDetail("errors", violations);
	}

}
