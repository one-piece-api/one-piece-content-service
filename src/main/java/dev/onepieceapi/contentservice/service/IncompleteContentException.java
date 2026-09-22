package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.exception.web.FieldViolation;

import java.util.List;

/**
 * Raised by {@code submitForReview} (UF-CNT-03,
 * docs/user-flows/authentication-and-user-management.md 3.1/3.3) when a required field is
 * missing or over its length limit for at least one active language - a draft may be
 * saved incomplete/over-length, but not submitted that way. A {@link DomainException}
 * (422), not a {@code ValidationException} (400): the submit request itself is
 * well-formed, it's the working revision's own content that doesn't yet satisfy the
 * business rule for this transition.
 */
public class IncompleteContentException extends DomainException {

	public IncompleteContentException(List<FieldViolation> violations) {
		super(ContentErrorCode.INCOMPLETE, "Working revision is not complete enough to submit for review");
		this.withDetail("errors", violations);
	}

}
