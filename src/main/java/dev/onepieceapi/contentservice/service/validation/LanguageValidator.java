package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.language.Language;
import dev.onepieceapi.contentservice.service.exception.InvalidLanguageCodeException;
import dev.onepieceapi.contentservice.service.exception.InvalidLanguageNameException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * The rules a language must meet to enter the catalog
 * (docs/user-flows/content-editorial-workflow.md 3.2). Each broken rule has its own
 * exception and error code, which the catalog screen tells apart.
 */
@Component
public class LanguageValidator {

	/** Exactly two lowercase ASCII letters - ISO 639-1 style, e.g. "fr", "es". */
	private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z]{2}$");

	private static final int MAX_NAME_LENGTH = 100;

	public void validate(Language language) {
		if (!CODE_PATTERN.matcher(language.code()).matches()) {
			throw new InvalidLanguageCodeException(language.code());
		}
		String name = language.name();
		if (name.isBlank() || name.length() > MAX_NAME_LENGTH) {
			throw new InvalidLanguageNameException();
		}
	}

}
