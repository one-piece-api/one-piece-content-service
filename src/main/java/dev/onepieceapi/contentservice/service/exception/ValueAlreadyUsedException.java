package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.exception.web.FieldViolation;

import java.util.List;

/**
 * Raised when a content is saved with a value another content already has, where it must
 * be unique (docs/user-flows/content-editorial-workflow.md 3.3). Names every field at
 * fault under {@code errors}, the same shape a failed request validation answers with.
 */
public class ValueAlreadyUsedException extends DomainException {

	public ValueAlreadyUsedException(List<FieldViolation> violations) {
		super(ContentErrorCode.VALUE_ALREADY_USED, "Another content already uses a value that must be unique");
		withDetail("errors", violations);
	}

}
