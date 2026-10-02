package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.NotFoundException;

import java.util.UUID;

/**
 * Raised when a version does not exist for the caller: no such content or number, or a
 * status they may not see - indistinguishable on purpose, as for
 * {@link DevilFruitTypeNotFoundException}.
 */
public class VersionNotFoundException extends NotFoundException {

	public VersionNotFoundException(UUID contentId, int versionNumber) {
		super(ContentErrorCode.VERSION_NOT_FOUND, "Version " + versionNumber + " of " + contentId + " not found");
	}

}
