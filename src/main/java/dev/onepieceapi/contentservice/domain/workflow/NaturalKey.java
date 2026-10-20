package dev.onepieceapi.contentservice.domain.workflow;

import java.util.List;
import java.util.Objects;

/**
 * The value an entity declares as what identifies one of its contents
 * (docs/user-flows/content-editorial-workflow.md 3.3): unique against every version of
 * every other content, and what the public address (slug) comes from - a romaji for a
 * Devil Fruit, a number for a chapter, a poster name and an amount for a bounty.
 *
 * @param field the field holding it, named as a request names it, e.g. {@code romaji};
 * for a composite key, the name the key goes by in block details
 * @param value the value itself; {@code null} while none is written
 * @param fields the request fields the value is made of, which a violation names one by
 * one; just {@code field} unless the key is composite
 */
public record NaturalKey(String field, Object value, List<String> fields) {

	public NaturalKey {
		fields = fields == null || fields.isEmpty() ? List.of(field) : List.copyOf(fields);
	}

	public NaturalKey(String field, Object value) {
		this(field, value, List.of(field));
	}

	/**
	 * The value as text, what the slug is made of; {@code null} while none is written.
	 */
	public String text() {
		return Objects.toString(this.value, null);
	}

	public boolean isMissing() {
		return this.value == null;
	}

}
