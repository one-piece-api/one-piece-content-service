package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.NotFoundException;

import java.util.UUID;

/**
 * Raised when a content does not exist for the caller: either there is none with this id,
 * or none of its versions is in a status they may see - the two are deliberately
 * indistinguishable (docs/user-flows/content-editorial-workflow.md 4.3).
 */
public class ArcNotFoundException extends NotFoundException {

	public ArcNotFoundException(UUID contentId) {
		super(ContentErrorCode.ARC_NOT_FOUND, "Arc " + contentId + " not found");
	}

}
