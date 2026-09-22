package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.NotFoundException;

import java.util.UUID;

/**
 * Raised when Rollback (Step 7) is asked to restore a version that either doesn't exist
 * or doesn't belong to the given item - same "scope the lookup to its claimed parent"
 * pattern as {@link WorkingRevisionNotFoundException}'s ownership check, just against an
 * item instead of an author.
 */
public class ContentVersionNotFoundException extends NotFoundException {

	public ContentVersionNotFoundException(UUID itemId, UUID versionId) {
		super(ContentErrorCode.CONTENT_VERSION_NOT_FOUND, "No version " + versionId + " for item " + itemId);
	}

}
