package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.exception.NotFoundException;

import java.util.UUID;

/**
 * Raised both when a working revision genuinely doesn't exist and when it exists but is
 * owned by a different author - deliberately the same exception either way. A draft is
 * visible only to its own author (docs/user-flows/authentication-and-user-management.md
 * 4.1/7.5, total isolation); returning {@code 403} for "exists but not yours" would leak
 * that something exists at this id, which is exactly what that isolation is meant to
 * prevent.
 */
public class WorkingRevisionNotFoundException extends NotFoundException {

	public WorkingRevisionNotFoundException(UUID id) {
		super(ContentErrorCode.WORKING_REVISION_NOT_FOUND, "No draft found for " + id);
	}

}
