package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.exception.ConflictException;

import java.util.UUID;

/**
 * Raised by {@code claim} (UF-CNT-13) when the working revision is already claimed by
 * another REVIEWER - a working revision may be claimed by only one REVIEWER at a time.
 */
public class ReviewAlreadyClaimedException extends ConflictException {

	public ReviewAlreadyClaimedException(UUID workingRevisionId) {
		super(ContentErrorCode.REVIEW_ALREADY_CLAIMED, "Working revision " + workingRevisionId + " is already claimed");
	}

}
