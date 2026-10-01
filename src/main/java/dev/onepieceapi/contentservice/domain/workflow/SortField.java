package dev.onepieceapi.contentservice.domain.workflow;

/**
 * A field a list can be sorted by, as its callers name it. Each kind of content declares
 * its own in an enum implementing this - e.g. {@code DevilFruitTypeSortField} - which is
 * then the single place that knows what a list of that kind can be sorted by.
 */
public interface SortField {

	/** The name used in a request, e.g. {@code sort=updatedAt,desc}. */
	String field();

}
