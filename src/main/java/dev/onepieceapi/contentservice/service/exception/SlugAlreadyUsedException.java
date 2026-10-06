package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.exception.web.FieldViolation;

import java.util.List;

/**
 * Raised when a romaji differs from every other content's, but would give the content the
 * public address (slug) of another one - e.g. the same romaji with a macron more
 * (docs/user-flows/content-editorial-workflow.md 3.3). Told apart from
 * {@link ValueAlreadyUsedException} because the editor sees no equal value to blame.
 */
public class SlugAlreadyUsedException extends DomainException {

	public SlugAlreadyUsedException(String slug, List<FieldViolation> violations) {
		super(ContentErrorCode.SLUG_ALREADY_USED,
				"Another content already has the public address '%s'".formatted(slug));
		withDetail("slug", slug);
		withDetail("errors", violations);
	}

}
