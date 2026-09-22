package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.exception.ConflictException;

import java.util.UUID;

/**
 * Raised by {@code submitForReview} (UF-CNT-03) when the same item already has a
 * different working revision {@code IN_REVIEW} - at most one working revision per item
 * may occupy the review queue slot at a time
 * (docs/user-flows/authentication-and-user-management.md 4.1). Deliberately not raised
 * when a sibling is merely {@code REVIEWED} - that case is handled by supersession at
 * approval time instead (Step 3).
 */
public class ReviewSlotOccupiedException extends ConflictException {

	public ReviewSlotOccupiedException(UUID itemId) {
		super(ContentErrorCode.REVIEW_SLOT_OCCUPIED,
				"Item " + itemId + " already has another working revision in review");
	}

}
