package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.NotFoundException;

import java.util.UUID;

/**
 * Raised when an item does not exist for the caller: either there is none with this id,
 * or none of its versions is in a status they may see - the two are deliberately
 * indistinguishable (docs/user-flows/content-editorial-workflow.md 4.3).
 */
public class DevilFruitTypeNotFoundException extends NotFoundException {

	public DevilFruitTypeNotFoundException(UUID itemId) {
		super(ContentErrorCode.DEVIL_FRUIT_TYPE_NOT_FOUND, "Devil Fruit Type " + itemId + " not found");
	}

}
