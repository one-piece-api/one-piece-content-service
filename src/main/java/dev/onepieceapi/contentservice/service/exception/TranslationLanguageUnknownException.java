package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.DomainException;

import java.util.Collection;

/**
 * Raised when a content is saved with a translation in a language the catalog does not
 * have (docs/user-flows/content-editorial-workflow.md 3.2).
 */
public class TranslationLanguageUnknownException extends DomainException {

	public TranslationLanguageUnknownException(Collection<String> codes) {
		super(ContentErrorCode.TRANSLATION_LANGUAGE_UNKNOWN, "Not a language of the catalog: " + codes);
		withDetail("languages", codes);
	}

}
