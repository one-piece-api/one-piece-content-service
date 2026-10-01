package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.ConflictException;

/**
 * Raised by {@code LanguageService#delete} when a version, in any status, carries a
 * translation in this language. The translation table references the catalog, so letting
 * this reach the database would surface as an opaque constraint violation instead of a
 * clean, actionable 409.
 */
public class LanguageInUseException extends ConflictException {

	public LanguageInUseException(String code) {
		super(ContentErrorCode.LANGUAGE_IN_USE, "Language " + code + " is still referenced by existing content");
	}

}
