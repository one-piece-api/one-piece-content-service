package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.ConflictException;

import java.util.UUID;

/**
 * Raised by {@code deleteDraft} (UF-CNT-11) when the item already has at least one entry
 * in its published-version history. Such an item can never have a working revision
 * hard-deleted, regardless of that revision's own status or how many others, by any
 * author, currently exist for the item - it can only be retired at the item level
 * (UF-CNT-10).
 */
public class CannotDeletePublishedItemException extends ConflictException {

	public CannotDeletePublishedItemException(UUID itemId) {
		super(ContentErrorCode.CANNOT_DELETE_PUBLISHED_ITEM,
				"Item " + itemId + " has published-version history and cannot have a working revision deleted");
	}

}
