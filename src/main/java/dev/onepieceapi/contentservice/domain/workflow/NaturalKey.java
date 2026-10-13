package dev.onepieceapi.contentservice.domain.workflow;

import java.util.Objects;

/**
 * The value an entity declares as what identifies one of its contents
 * (docs/user-flows/content-editorial-workflow.md 3.3): unique against every version of
 * every other content, and what the public address (slug) comes from - a romaji for a
 * Devil Fruit, a number for a chapter.
 *
 * @param field the field holding it, named as a request names it, e.g. {@code romaji}
 * @param value the value itself; {@code null} while none is written
 */
public record NaturalKey(String field, Object value) {

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
