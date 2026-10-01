package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.ValidationException;

/**
 * Raised by {@code LanguageService#create} when the requested code isn't exactly two
 * lowercase ASCII letters (ISO 639-1 style, e.g. "fr", "es") -
 * docs/user-flows/content-editorial-workflow.md 3.2.
 */
public class InvalidLanguageCodeException extends ValidationException {

	public InvalidLanguageCodeException(String code) {
		super(ContentErrorCode.INVALID_LANGUAGE_CODE, "Invalid language code: " + code);
	}

}
