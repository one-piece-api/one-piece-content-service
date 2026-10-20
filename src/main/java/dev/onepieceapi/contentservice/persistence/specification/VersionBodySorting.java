package dev.onepieceapi.contentservice.persistence.specification;

import dev.onepieceapi.contentservice.domain.bounty.BountySortField;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns the sort a caller asks for, expressed in {@link ContentSortField}s, into the
 * order the list query runs with. Some fields are not a column but a computed value - the
 * name in the caller's language, the status in lifecycle order - so the order is built
 * with the Criteria API, as a {@link Specification} joined to the list's conditions,
 * rather than as a {@link Sort} of entity paths.
 */
@UtilityClass
public class VersionBodySorting {

	/** How each sortable field is read from a row of the list query. */
	@FunctionalInterface
	private interface SortKey {

		Expression<?> of(Root<? extends VersionBodyEntity> root, CriteriaBuilder cb, String language, String naturalKey,
				boolean hasLocalizedNames);

	}

	/**
	 * By the field's name in a request: the entities that sort by more have their own
	 * enum.
	 */
	private static final Map<String, SortKey> KEYS = new HashMap<>();

	static {
		KEYS.put(ContentSortField.UPDATED_AT.field(), (root, cb, language, naturalKey,
				hasLocalizedNames) -> workflowOf(root).get(ContentVersionEntity.Fields.updatedAt));
		KEYS.put(ContentSortField.ROMAJI.field(),
				(root, cb, language, naturalKey, hasLocalizedNames) -> naturalKeyOf(root, cb, naturalKey));
		KEYS.put(ContentSortField.NUMBER.field(),
				(root, cb, language, naturalKey, hasLocalizedNames) -> naturalKeyOf(root, cb, naturalKey));
		KEYS.put(ContentSortField.NAME.field(), VersionBodySorting::nameOf);
		KEYS.put(ContentSortField.AUTHOR.field(),
				(root, cb, language, naturalKey, hasLocalizedNames) -> authorOf(root));
		KEYS.put(ContentSortField.STATUS.field(),
				(root, cb, language, naturalKey, hasLocalizedNames) -> statusRankOf(root, cb));
		KEYS.put(BountySortField.AMOUNT.field(),
				(root, cb, language, naturalKey, hasLocalizedNames) -> root.get(BountySortField.AMOUNT.field()));
	}

	/** Last update, newest first - what the list shows when no sort is asked for. */
	private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, ContentSortField.UPDATED_AT.field());

	/**
	 * The list query ordered as {@code requested}, or by the default sort; then by
	 * content, so that rows with equal values keep a stable order across pages. Rows
	 * missing the value sorted by come last in either direction; {@code naturalKey} names
	 * the attribute holding the entity's natural key, and {@code hasLocalizedNames} tells
	 * whether the entity has a name to sort by instead. A count query is left alone: the
	 * order would not change it.
	 */
	public <E extends VersionBodyEntity> Specification<E> orderedBy(Sort requested, String language, String naturalKey,
			boolean hasLocalizedNames) {
		return (root, query, cb) -> {
			if (!isCount(query)) {
				query.orderBy(ordersFor(requested.isSorted() ? requested : DEFAULT_SORT, root, cb, language, naturalKey,
						hasLocalizedNames));
			}
			return null;
		};
	}

	private static List<Order> ordersFor(Sort sort, Root<? extends VersionBodyEntity> root, CriteriaBuilder cb,
			String language, String naturalKey, boolean hasLocalizedNames) {
		List<Order> orders = new ArrayList<>();
		sort.forEach(order -> orders.add(toOrder(order, root, cb, language, naturalKey, hasLocalizedNames)));
		orders.add(cb.asc(workflowOf(root).get(ContentVersionEntity.Fields.contentId)));
		return orders;
	}

	private static Order toOrder(Sort.Order order, Root<? extends VersionBodyEntity> root, CriteriaBuilder cb,
			String language, String naturalKey, boolean hasLocalizedNames) {
		Expression<?> key = keyOf(order).of(root, cb, language, naturalKey, hasLocalizedNames);
		return order.isAscending() ? cb.asc(key, Nulls.LAST) : cb.desc(key, Nulls.LAST);
	}

	/** The field has been validated on the way in: an unknown one here is a bug. */
	private static SortKey keyOf(Sort.Order order) {
		return Optional.ofNullable(KEYS.get(order.getProperty()))
			.orElseThrow(() -> new IllegalArgumentException("Not a sortable field: " + order.getProperty()));
	}

	/**
	 * The name in {@code language}, or else the natural key - case-insensitive. Joins the
	 * one translation in that language, if the version has it. Without a localized name,
	 * the natural key alone.
	 */
	private static Expression<String> nameOf(Root<? extends VersionBodyEntity> root, CriteriaBuilder cb,
			String language, String naturalKey, boolean hasLocalizedNames) {
		Expression<String> key = VersionBodySpecifications.naturalKeyText(root, cb, naturalKey);
		if (!hasLocalizedNames) {
			return cb.lower(key);
		}
		MapJoin<? extends VersionBodyEntity, String, TranslationEmbeddable> translation = root
			.joinMap(VersionBodyEntity.TRANSLATIONS, JoinType.LEFT);
		translation.on(cb.equal(translation.key(), language));
		Expression<String> name = translation.value().get(TranslationEmbeddable.Fields.name);
		return cb.coalesce(cb.lower(name), cb.lower(key));
	}

	/** A text case-insensitive, any other value - e.g. a number - in its own order. */
	private static Expression<?> naturalKeyOf(Root<? extends VersionBodyEntity> root, CriteriaBuilder cb,
			String naturalKey) {
		Path<?> key = root.get(naturalKey);
		return String.class.equals(key.getJavaType()) ? cb.lower(root.get(naturalKey)) : key;
	}

	private static Path<String> authorOf(Root<? extends VersionBodyEntity> root) {
		Path<UserEmbeddable> author = workflowOf(root).get(ContentVersionEntity.Fields.author);
		return author.get(UserEmbeddable.Fields.username);
	}

	/**
	 * The position of the status in the lifecycle, as the enum declares it: stored as
	 * text, the status would otherwise sort alphabetically.
	 */
	private static Expression<Integer> statusRankOf(Root<? extends VersionBodyEntity> root, CriteriaBuilder cb) {
		CriteriaBuilder.SimpleCase<VersionStatus, Integer> rank = cb
			.selectCase(workflowOf(root).get(ContentVersionEntity.Fields.status));
		for (VersionStatus status : VersionStatus.values()) {
			rank = rank.when(status, status.ordinal());
		}
		return rank.otherwise(VersionStatus.values().length);
	}

	private static Path<ContentVersionEntity> workflowOf(Root<? extends VersionBodyEntity> root) {
		return root.get(VersionBodyEntity.Fields.version);
	}

	/** Spring Data runs the total of a page as a separate query counting the rows. */
	private static boolean isCount(CriteriaQuery<?> query) {
		return Long.class.equals(query.getResultType());
	}

}
