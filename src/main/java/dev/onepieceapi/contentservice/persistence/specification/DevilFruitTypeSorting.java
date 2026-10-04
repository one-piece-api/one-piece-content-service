package dev.onepieceapi.contentservice.persistence.specification;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSortField;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.UserEmbeddable;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the sort a caller asks for, expressed in {@link DevilFruitTypeSortField}s, into
 * the order the list query runs with. Some fields are not a column but a computed value -
 * the name in the caller's language, the status in lifecycle order - so the order is
 * built with the Criteria API, as a {@link Specification} joined to the list's
 * conditions, rather than as a {@link Sort} of entity paths.
 */
@UtilityClass
public class DevilFruitTypeSorting {

	/** How each sortable field is read from a row of the list query. */
	@FunctionalInterface
	private interface SortKey {

		Expression<?> of(Root<DevilFruitTypeVersionEntity> root, CriteriaBuilder cb, String language);

	}

	private static final Map<DevilFruitTypeSortField, SortKey> KEYS = new EnumMap<>(DevilFruitTypeSortField.class);

	static {
		KEYS.put(DevilFruitTypeSortField.UPDATED_AT,
				(root, cb, language) -> workflowOf(root).get(ContentVersionEntity.Fields.updatedAt));
		KEYS.put(DevilFruitTypeSortField.ROMAJI, (root, cb, language) -> romajiOf(root, cb));
		KEYS.put(DevilFruitTypeSortField.NAME, DevilFruitTypeSorting::nameOf);
		KEYS.put(DevilFruitTypeSortField.AUTHOR, (root, cb, language) -> authorOf(root));
		KEYS.put(DevilFruitTypeSortField.STATUS, (root, cb, language) -> statusRankOf(root, cb));
	}

	/** Last update, newest first - what the list shows when no sort is asked for. */
	private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, DevilFruitTypeSortField.UPDATED_AT.field());

	/**
	 * The list query ordered as {@code requested}, or by the default sort; then by
	 * content, so that rows with equal values keep a stable order across pages. Rows
	 * missing the value sorted by come last in either direction. A count query is left
	 * alone: the order would not change it.
	 */
	public Specification<DevilFruitTypeVersionEntity> orderedBy(Sort requested, String language) {
		return (root, query, cb) -> {
			if (!isCount(query)) {
				query.orderBy(ordersFor(requested.isSorted() ? requested : DEFAULT_SORT, root, cb, language));
			}
			return null;
		};
	}

	private static List<Order> ordersFor(Sort sort, Root<DevilFruitTypeVersionEntity> root, CriteriaBuilder cb,
			String language) {
		List<Order> orders = new ArrayList<>();
		sort.forEach(order -> orders.add(toOrder(order, root, cb, language)));
		orders.add(cb.asc(workflowOf(root).get(ContentVersionEntity.Fields.contentId)));
		return orders;
	}

	private static Order toOrder(Sort.Order order, Root<DevilFruitTypeVersionEntity> root, CriteriaBuilder cb,
			String language) {
		Expression<?> key = keyOf(order).of(root, cb, language);
		return order.isAscending() ? cb.asc(key, Nulls.LAST) : cb.desc(key, Nulls.LAST);
	}

	/** The field has been validated on the way in: an unknown one here is a bug. */
	private static SortKey keyOf(Sort.Order order) {
		return DevilFruitTypeSortField.of(order.getProperty())
			.map(KEYS::get)
			.orElseThrow(() -> new IllegalArgumentException("Not a sortable field: " + order.getProperty()));
	}

	/**
	 * The name in {@code language}, or else the romaji - case-insensitive. Joins the one
	 * translation in that language, if the version has it.
	 */
	private static Expression<String> nameOf(Root<DevilFruitTypeVersionEntity> root, CriteriaBuilder cb,
			String language) {
		MapJoin<DevilFruitTypeVersionEntity, String, TranslationEmbeddable> translation = root
			.joinMap(DevilFruitTypeVersionEntity.Fields.translations, JoinType.LEFT);
		translation.on(cb.equal(translation.key(), language));
		Expression<String> name = translation.value().get(TranslationEmbeddable.Fields.name);
		return cb.coalesce(cb.lower(name), romajiOf(root, cb));
	}

	private static Expression<String> romajiOf(Root<DevilFruitTypeVersionEntity> root, CriteriaBuilder cb) {
		return cb.lower(root.get(DevilFruitTypeVersionEntity.Fields.romaji));
	}

	private static Path<String> authorOf(Root<DevilFruitTypeVersionEntity> root) {
		Path<UserEmbeddable> author = workflowOf(root).get(ContentVersionEntity.Fields.author);
		return author.get(UserEmbeddable.Fields.username);
	}

	/**
	 * The position of the status in the lifecycle, as the enum declares it: stored as
	 * text, the status would otherwise sort alphabetically.
	 */
	private static Expression<Integer> statusRankOf(Root<DevilFruitTypeVersionEntity> root, CriteriaBuilder cb) {
		CriteriaBuilder.SimpleCase<VersionStatus, Integer> rank = cb
			.selectCase(workflowOf(root).get(ContentVersionEntity.Fields.status));
		for (VersionStatus status : VersionStatus.values()) {
			rank = rank.when(status, status.ordinal());
		}
		return rank.otherwise(VersionStatus.values().length);
	}

	private static Path<ContentVersionEntity> workflowOf(Root<DevilFruitTypeVersionEntity> root) {
		return root.get(DevilFruitTypeVersionEntity.Fields.version);
	}

	/** Spring Data runs the total of a page as a separate query counting the rows. */
	private static boolean isCount(CriteriaQuery<?> query) {
		return Long.class.equals(query.getResultType());
	}

}
