package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.exception.web.FieldViolation;

import java.util.List;

/**
 * Raised when a content is saved with a value its rules refuse beyond its length - such
 * as a romaji that gives no slug (docs/user-flows/content-editorial-workflow.md 3.3).
 * Names every field at fault under {@code errors}, like
 * {@link ValueAlreadyUsedException}.
 */
public class ValueInvalidException extends DomainException {

	public ValueInvalidException(List<FieldViolation> violations) {
		super(ContentErrorCode.VALUE_INVALID, "A value does not meet the rules of its field");
		withDetail("errors", violations);
	}

}
