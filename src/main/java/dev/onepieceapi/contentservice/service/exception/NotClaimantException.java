package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.ConflictException;

/**
 * Raised by {@code release}/{@code approve}/{@code reject} (UF-CNT-05/06/14) when the
 * acting REVIEWER does not currently hold the claim on this working revision - only the
 * current claimant may act on it.
 */
public class NotClaimantException extends ConflictException {

	public NotClaimantException(String action) {
		super(ContentErrorCode.NOT_CLAIMANT, "Cannot " + action + " a working revision you have not claimed");
	}

}
