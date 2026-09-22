package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.DomainException;

/**
 * Raised by {@code reject} (UF-CNT-06) when no reason is given. A {@link DomainException}
 * (422), not a {@code ValidationException} (400): the request is well-formed, it's the
 * business rule that a rejection must always explain what to correct.
 */
public class MissingRejectionReasonException extends DomainException {

	public MissingRejectionReasonException() {
		super(ContentErrorCode.MISSING_REJECTION_REASON, "A rejection reason is required");
	}

}
