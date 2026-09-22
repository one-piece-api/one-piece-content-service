package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.ValidationException;
import dev.onepieceapi.exception.web.FieldViolation;

import java.util.Set;

/**
 * Raised when an edit request references a language code that isn't in the (currently
 * read-only) language catalog - docs/user-flows/authentication-and-user-management.md
 * 3.2.
 */
public class UnknownLanguageException extends ValidationException {

	public UnknownLanguageException(Set<String> unknownCodes) {
		super(ContentErrorCode.UNKNOWN_LANGUAGE, "Unknown language code(s): " + unknownCodes);
		var violations = unknownCodes.stream()
			.map(code -> new FieldViolation("translations." + code, "not an active language"))
			.toList();
		this.withDetail("errors", violations);
	}

}
