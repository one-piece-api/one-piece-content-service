package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.ConflictException;

/**
 * Raised by {@code LanguageService#delete} (Step 10) when at least one working revision
 * or published version already carries a translation in this language -
 * {@code language.code} is referenced by a {@code REFERENCES} foreign key from both
 * translation tables, so letting this reach the database would surface as an opaque
 * constraint-violation 500 instead of a clean, actionable 409 - same precedent as
 * {@code one-piece-user-service}'s {@code ROLE_IN_USE}/{@code PERMISSION_IN_USE}.
 */
public class LanguageInUseException extends ConflictException {

	public LanguageInUseException(String code) {
		super(ContentErrorCode.LANGUAGE_IN_USE, "Language " + code + " is still referenced by existing content");
	}

}
