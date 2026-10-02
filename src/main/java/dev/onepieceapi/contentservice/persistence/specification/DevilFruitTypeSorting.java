package dev.onepieceapi.contentservice.persistence.specification;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSortField;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the sort a caller asks for, expressed in {@link DevilFruitTypeSortField}s, into
 * the one the query runs with, expressed in entity paths.
 */
@UtilityClass
public class DevilFruitTypeSorting {

	/** The path from a Devil Fruit Type version to a field of its workflow side. */
	private static final String WORKFLOW = DevilFruitTypeVersionEntity.Fields.version + ".";

	/** Where each sortable field lives. */
	private static final Map<DevilFruitTypeSortField, String> PATHS = new EnumMap<>(DevilFruitTypeSortField.class);

	static {
		PATHS.put(DevilFruitTypeSortField.UPDATED_AT, WORKFLOW + ContentVersionEntity.Fields.updatedAt);
		PATHS.put(DevilFruitTypeSortField.ROMAJI, DevilFruitTypeVersionEntity.Fields.romaji);
	}

	/** Last update, newest first - what the list shows when no sort is asked for. */
	private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC,
			PATHS.get(DevilFruitTypeSortField.UPDATED_AT));

	/**
	 * Always appended, so that rows with equal values keep a stable order across pages.
	 */
	private static final Sort TIE_BREAKER = Sort.by(WORKFLOW + ContentVersionEntity.Fields.contentId);

	/** The same page, sorted the way the query understands. */
	public Pageable resolve(Pageable requested) {
		Sort sort = requested.getSort().isSorted() ? toPaths(requested.getSort()) : DEFAULT_SORT;
		return PageRequest.of(requested.getPageNumber(), requested.getPageSize(), sort.and(TIE_BREAKER));
	}

	private static Sort toPaths(Sort requested) {
		List<Sort.Order> orders = requested.stream().map(DevilFruitTypeSorting::toPathOrder).toList();
		return Sort.by(orders);
	}

	/** The same order, on the entity path of its field. */
	private static Sort.Order toPathOrder(Sort.Order order) {
		return order.withProperty(pathOf(order));
	}

	/** The field has been validated on the way in: an unknown one here is a bug. */
	private static String pathOf(Sort.Order order) {
		return DevilFruitTypeSortField.of(order.getProperty())
			.map(PATHS::get)
			.orElseThrow(() -> new IllegalArgumentException("Not a sortable field: " + order.getProperty()));
	}

}
