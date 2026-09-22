package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.exception.ConflictException;

import java.util.UUID;

/**
 * Raised by {@code claim} (UF-CNT-13) when the working revision is already claimed by
 * another REVIEWER - a working revision may be claimed by only one REVIEWER at a time.
 * Also raised by {@code withdrawToDraft} (UF-CNT-04) when the acting author tries to
 * withdraw a working revision a REVIEWER currently holds the claim on - pulling it back
 * to {@code DRAFT} out from under them would silently discard their in-progress work.
 */
public class ReviewAlreadyClaimedException extends ConflictException {

	public ReviewAlreadyClaimedException(UUID workingRevisionId) {
		super(ContentErrorCode.REVIEW_ALREADY_CLAIMED, "Working revision " + workingRevisionId + " is already claimed");
	}

}
