package dev.onepieceapi.contentservice.persistence.specification;

import dev.onepieceapi.contentservice.domain.dashboard.DashboardSortField;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the sort a caller asks of a dashboard status page, expressed in
 * {@link DashboardSortField}s, into the one the query over the shared workflow table runs
 * with - as {@link VersionBodySorting} does for an entity section.
 */
@UtilityClass
public class ContentVersionSorting {

	/** Where each sortable field lives. */
	private static final Map<DashboardSortField, String> PATHS = new EnumMap<>(DashboardSortField.class);

	static {
		PATHS.put(DashboardSortField.UPDATED_AT, ContentVersionEntity.Fields.updatedAt);
	}

	/** Last update, newest first - what a status page shows when no sort is asked for. */
	private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, PATHS.get(DashboardSortField.UPDATED_AT));

	/**
	 * Always appended, so that rows with equal values keep a stable order across pages.
	 */
	private static final Sort TIE_BREAKER = Sort.by(ContentVersionEntity.Fields.contentId);

	/** The same page, sorted the way the query understands. */
	public Pageable resolve(Pageable requested) {
		Sort sort = requested.getSort().isSorted() ? toPaths(requested.getSort()) : DEFAULT_SORT;
		return PageRequest.of(requested.getPageNumber(), requested.getPageSize(), sort.and(TIE_BREAKER));
	}

	private static Sort toPaths(Sort requested) {
		List<Sort.Order> orders = requested.stream().map(ContentVersionSorting::toPathOrder).toList();
		return Sort.by(orders);
	}

	/** The field has been validated on the way in: an unknown one here is a bug. */
	private static Sort.Order toPathOrder(Sort.Order order) {
		return order.withProperty(DashboardSortField.of(order.getProperty())
			.map(PATHS::get)
			.orElseThrow(() -> new IllegalArgumentException("Not a sortable field: " + order.getProperty())));
	}

}
