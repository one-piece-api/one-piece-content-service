package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.NotFoundException;

import java.util.UUID;

/**
 * Raised when a bounty does not exist for the caller: either there is none with this id,
 * or none of its versions is in a status they may see - the two are deliberately
 * indistinguishable (docs/user-flows/content-editorial-workflow.md 4.3).
 */
public class BountyNotFoundException extends NotFoundException {

	public BountyNotFoundException(UUID contentId) {
		super(ContentErrorCode.BOUNTY_NOT_FOUND, "Bounty " + contentId + " not found");
	}

}
