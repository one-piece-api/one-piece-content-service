package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.ValidationException;

/**
 * Raised by {@code LanguageService#create} (Step 10) when the display name is blank or
 * exceeds {@code language.name}'s column length (100).
 */
public class InvalidLanguageNameException extends ValidationException {

	public InvalidLanguageNameException() {
		super(ContentErrorCode.INVALID_LANGUAGE_NAME, "Language name must be non-blank and at most 100 characters");
	}

}
