package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.DomainException;

/**
 * Raised by {@code submitForReview} (3.3, user-reported gap: nothing stopped an author
 * from submitting - and a PUBLISHER from approving/publishing - several byte-for-byte
 * identical versions in a row) when the working revision's romaji and every active
 * language's name/description already match a version already published for this same
 * item - checked against every version in the item's history, not just the current live
 * one: resubmitting an old, no-longer-live snapshot verbatim is just as much "no change"
 * as matching the current live one. A {@link DomainException} (422): the request is
 * well-formed, there is simply nothing new in it to review or publish.
 */
public class IdenticalToExistingVersionException extends DomainException {

	public IdenticalToExistingVersionException(int sequenceNumber) {
		super(ContentErrorCode.IDENTICAL_TO_EXISTING_VERSION,
				"Working revision is identical to version " + sequenceNumber);
	}

}
