package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.ConflictException;

/**
 * Raised by {@code LanguageService#create} (Step 10) when the code is already in the
 * catalog.
 */
public class LanguageAlreadyExistsException extends ConflictException {

	public LanguageAlreadyExistsException(String code) {
		super(ContentErrorCode.LANGUAGE_ALREADY_EXISTS, "Language " + code + " already exists");
	}

}
