package dev.onepieceapi.contentservice.domain.readertime;

/**
 * A window of an item with where a request names it, so a rule broken by one of its
 * points names that point.
 *
 * @param field the entry holding it, e.g. {@code species[1]} or
 * {@code affiliations[0].periods[1]}
 */
public record NamedWindow(String field, StoryWindow window) {

	/** The point {@code discovered}, as a request names it. */
	public static final String DISCOVERED = "discovered";

	/** The point {@code ended}, as a request names it. */
	public static final String ENDED = "ended";

	/** The field of one of its points, e.g. {@code species[1].discovered}. */
	public String pointField(String point) {
		return this.field + "." + point;
	}

}
