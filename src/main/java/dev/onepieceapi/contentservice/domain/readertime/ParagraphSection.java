package dev.onepieceapi.contentservice.domain.readertime;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The long texts of an item, each a list of paragraphs
 * (docs/user-flows/content-editorial-workflow.md 3.7.3): every paragraphed entity has a
 * description; the Devil Fruit and its type also advantages and disadvantages.
 */
@Getter
@RequiredArgsConstructor
public enum ParagraphSection {

	DESCRIPTION("description"), ADVANTAGES("advantages"), DISADVANTAGES("disadvantages");

	/** The list as a request names it. */
	private final String field;

	/** One of its paragraphs as a request names it, e.g. {@code description[1]}. */
	public String entryField(int index) {
		return this.field + "[" + index + "]";
	}

}
