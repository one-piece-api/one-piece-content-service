package dev.onepieceapi.contentservice.domain.workflow;

/**
 * A field a list can be sorted by, as its callers name it. Each kind of list declares its
 * own in an enum implementing this - {@link ContentSortField} for every entity section,
 * {@code DashboardSortField} for the status pages - which is then the single place that
 * knows what that list can be sorted by.
 */
public interface SortField {

	/** The name used in a request, e.g. {@code sort=updatedAt,desc}. */
	String field();

}
