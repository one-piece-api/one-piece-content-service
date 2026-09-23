package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.DomainException;
import dev.onepieceapi.exception.web.FieldViolation;

import java.util.List;

/**
 * Raised by {@code submitForReview}
 * (docs/user-flows/authentication-and-user-management.md 3.3) when the romaji or a
 * per-language name would collide with another Devil Fruit Type item's
 * already-submitted/reviewed/published content - checked only once the working revision
 * is otherwise complete ({@link IncompleteContentException} takes priority, since there
 * is no point flagging a collision on a still-blank field), and only against every
 * <em>other</em> item's {@code IN_REVIEW}/{@code REVIEWED}/{@code PUBLISHED} working
 * revisions - never a still-private {@code DRAFT} elsewhere (draft isolation, 7.5, stays
 * total: this never reveals that another author's invisible draft holds the same name),
 * never {@code SUPERSEDED} (out of the active pipeline), and never this same item's own
 * history (resubmitting or re-editing your own item is a replacement, not a collision). A
 * {@link DomainException} (422), same category as {@link IncompleteContentException}: the
 * request itself is well-formed, it's the working revision's own content that doesn't yet
 * satisfy the business rule.
 */
public class DuplicateContentException extends DomainException {

	public DuplicateContentException(List<FieldViolation> violations) {
		super(ContentErrorCode.DUPLICATE_CONTENT, "Working revision's romaji or name collides with another item");
		this.withDetail("errors", violations);
	}

}
