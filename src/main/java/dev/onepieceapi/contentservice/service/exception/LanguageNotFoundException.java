package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.NotFoundException;

/**
 * Raised by {@code LanguageService#delete} (Step 10) when the code isn't in the catalog.
 */
public class LanguageNotFoundException extends NotFoundException {

	public LanguageNotFoundException(String code) {
		super(ContentErrorCode.LANGUAGE_NOT_FOUND, "Language " + code + " not found");
	}

}
