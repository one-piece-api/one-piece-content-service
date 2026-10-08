package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.NotFoundException;

/**
 * Raised for an image that does not exist or that no version visible to the caller uses -
 * told alike, as for versions, so nobody learns which images exist.
 */
public class ImageNotFoundException extends NotFoundException {

	public ImageNotFoundException(String id) {
		super(ContentErrorCode.IMAGE_NOT_FOUND, "Image " + id + " not found");
	}

}
