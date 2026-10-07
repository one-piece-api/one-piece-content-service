package dev.onepieceapi.contentservice.persistence.specification;

import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.UserEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The building blocks of the list query of any entity section (UF-CNT-18), following the
 * Specification pattern: each method is one condition, and the caller ANDs together the
 * ones it needs - so a page and its total always come from the same predicate. The
 * conditions are about one row per content, then about that row: its workflow (the shared
 * version) or what it says. They read only what every entity has (see
 * {@link VersionBodyEntity}), so one set serves every section.
 */
@UtilityClass
public class VersionBodySpecifications {

	private static final char LIKE_ESCAPE = '\\';

	private static final String RELATION_SUFFIX = "ContentId";

	/**
	 * The whole list query: one row per content among {@code statuses}, then each filter
	 * that was actually given.
	 */
	public <E extends VersionBodyEntity> Specification<E> listOf(Collection<VersionStatus> statuses,
			ContentFilter filter, Clock clock) {
		List<Specification<E>> conditions = new ArrayList<>();
		conditions.add(mostRecentIn(statuses));
		filter.text().ifPresent(text -> conditions.add(matching(text)));
		if (filter.author() != null) {
			conditions.add(authoredBy(filter.author()));
		}
		filter.updatedSince(clock).ifPresent(instant -> conditions.add(updatedSince(instant)));
		filter.related().forEach((relation, contentId) -> conditions.add(relatedTo(relation, contentId)));
		return Specification.allOf(conditions);
	}

	/**
	 * Keeps, for each content, only its highest-numbered version among {@code statuses} -
	 * the one representing the content in the list. One row per content, so counting the
	 * rows counts the contents.
	 */
	public <E extends VersionBodyEntity> Specification<E> mostRecentIn(Collection<VersionStatus> statuses) {
		return (root, query, cb) -> ContentVersionSpecifications.mostRecentIn(workflowOf(root), statuses, query, cb);
	}

	public <E extends VersionBodyEntity> Specification<E> ofContents(Collection<UUID> contentIds) {
		return (root, query, cb) -> workflowOf(root).get(ContentVersionEntity.Fields.contentId).in(contentIds);
	}

	public <E extends VersionBodyEntity> Specification<E> authoredBy(String username) {
		return (root, query, cb) -> {
			Path<UserEmbeddable> author = workflowOf(root).get(ContentVersionEntity.Fields.author);
			return cb.equal(author.get(UserEmbeddable.Fields.username), username);
		};
	}

	/**
	 * "This version points, through this relation, to this content": the relation is the
	 * attribute of the entity named as it, with {@code ContentId} after (a fruit's
	 * {@code type} is its {@code typeContentId}).
	 */
	public <E extends VersionBodyEntity> Specification<E> relatedTo(String relation, UUID contentId) {
		return (root, query, cb) -> cb.equal(root.get(relation + RELATION_SUFFIX), contentId);
	}

	public <E extends VersionBodyEntity> Specification<E> updatedSince(Instant instant) {
		return (root, query, cb) -> {
			Path<Instant> updatedAt = workflowOf(root).get(ContentVersionEntity.Fields.updatedAt);
			return cb.greaterThanOrEqualTo(updatedAt, instant);
		};
	}

	/** Case-insensitive "contains", on the romaji or on the name in any language. */
	public <E extends VersionBodyEntity> Specification<E> matching(String text) {
		return (root, query, cb) -> {
			String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
			Predicate inRomaji = contains(cb, root.get(VersionBodyEntity.Fields.romaji), pattern);
			return cb.or(inRomaji, cb.exists(anyNameContaining(pattern, root, query.subquery(Integer.class), cb)));
		};
	}

	/** "This version has a translation whose name contains the text", as a subquery. */
	private static <E extends VersionBodyEntity> Subquery<Integer> anyNameContaining(String pattern, Root<E> root,
			Subquery<Integer> subquery, CriteriaBuilder cb) {
		Root<E> sameVersion = subquery.correlate(root);
		MapJoin<E, String, TranslationEmbeddable> translation = sameVersion.joinMap(VersionBodyEntity.TRANSLATIONS);
		Predicate inName = contains(cb, translation.value().get(TranslationEmbeddable.Fields.name), pattern);
		return subquery.select(cb.literal(1)).where(inName);
	}

	private static <E extends VersionBodyEntity> Path<ContentVersionEntity> workflowOf(Root<E> root) {
		return root.get(VersionBodyEntity.Fields.version);
	}

	private static Predicate contains(CriteriaBuilder cb, Expression<String> field, String pattern) {
		return cb.like(cb.lower(field), pattern, LIKE_ESCAPE);
	}

	/** So that a "%" or "_" typed by the user is searched for, not interpreted. */
	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
