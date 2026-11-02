package dev.onepieceapi.contentservice.domain.readertime;

import java.util.Set;

/**
 * The point of a reveal with where a request names it, so a rule broken by the point
 * names it (docs/user-flows/content-editorial-workflow.md 3.7.1, 3.7.4).
 *
 * @param field the reveal, e.g. {@code reveals[2]}
 * @param point where the reader learns
 * @param fieldNames the fields it names, as a request names them: two reveals naming the
 * same field never share a point on an axis
 */
public record NamedReveal(String field, StoryPoint point, Set<String> fieldNames) {

	/** Its point, as a request names it. */
	public static final String REVEALED_AT = "revealedAt";

	public NamedReveal {
		fieldNames = Set.copyOf(fieldNames);
	}

	/** The point as a request names it, e.g. {@code reveals[2].revealedAt}. */
	public String pointField() {
		return this.field + "." + REVEALED_AT;
	}

}
