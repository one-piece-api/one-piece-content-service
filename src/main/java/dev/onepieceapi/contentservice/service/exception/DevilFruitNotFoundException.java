package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.NotFoundException;

import java.util.UUID;

/**
 * Raised when a Devil Fruit does not exist for the caller: either there is none with this
 * id, or none of its versions is in a status they may see - the two are deliberately
 * indistinguishable (docs/user-flows/content-editorial-workflow.md 4.3).
 */
public class DevilFruitNotFoundException extends NotFoundException {

	public DevilFruitNotFoundException(UUID contentId) {
		super(ContentErrorCode.DEVIL_FRUIT_NOT_FOUND, "Devil Fruit " + contentId + " not found");
	}

}
