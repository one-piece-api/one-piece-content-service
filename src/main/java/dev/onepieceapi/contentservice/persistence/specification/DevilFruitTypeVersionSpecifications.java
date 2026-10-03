package dev.onepieceapi.contentservice.persistence.specification;

import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.UserEmbeddable;
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
import java.util.Optional;
import java.util.UUID;

/**
 * The building blocks of the Devil Fruit Type list query (UF-CNT-18), following the
 * Specification pattern: each method is one condition, and the caller ANDs together the
 * ones it needs - so a page and its total always come from the same predicate. The
 * conditions are about one row per content, then about that row: its workflow (the shared
 * version) or what it says.
 */
@UtilityClass
public class DevilFruitTypeVersionSpecifications {

	private static final char LIKE_ESCAPE = '\\';

	/**
	 * The whole list query: one row per content among {@code statuses}, then each filter
	 * that was actually given.
	 */
	public Specification<DevilFruitTypeVersionEntity> listOf(Collection<VersionStatus> statuses, ContentFilter filter,
			Clock clock) {
		List<Specification<DevilFruitTypeVersionEntity>> conditions = new ArrayList<>();
		conditions.add(mostRecentIn(statuses));
		filter.text().map(DevilFruitTypeVersionSpecifications::matching).ifPresent(conditions::add);
		Optional.ofNullable(filter.author())
			.map(DevilFruitTypeVersionSpecifications::authoredBy)
			.ifPresent(conditions::add);
		filter.updatedSince(clock).map(DevilFruitTypeVersionSpecifications::updatedSince).ifPresent(conditions::add);
		return Specification.allOf(conditions);
	}

	/**
	 * Keeps, for each content, only its highest-numbered version among {@code statuses} -
	 * the one representing the content in the list. One row per content, so counting the
	 * rows counts the contents.
	 */
	public Specification<DevilFruitTypeVersionEntity> mostRecentIn(Collection<VersionStatus> statuses) {
		return (root, query, cb) -> ContentVersionSpecifications.mostRecentIn(workflowOf(root), statuses, query, cb);
	}

	public Specification<DevilFruitTypeVersionEntity> ofContents(Collection<UUID> contentIds) {
		return (root, query, cb) -> workflowOf(root).get(ContentVersionEntity.Fields.contentId).in(contentIds);
	}

	public Specification<DevilFruitTypeVersionEntity> authoredBy(String username) {
		return (root, query, cb) -> {
			Path<UserEmbeddable> author = workflowOf(root).get(ContentVersionEntity.Fields.author);
			return cb.equal(author.get(UserEmbeddable.Fields.username), username);
		};
	}

	public Specification<DevilFruitTypeVersionEntity> updatedSince(Instant instant) {
		return (root, query, cb) -> {
			Path<Instant> updatedAt = workflowOf(root).get(ContentVersionEntity.Fields.updatedAt);
			return cb.greaterThanOrEqualTo(updatedAt, instant);
		};
	}

	/** Case-insensitive "contains", on the romaji or on the name in any language. */
	public Specification<DevilFruitTypeVersionEntity> matching(String text) {
		return (root, query, cb) -> {
			String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
			Predicate inRomaji = contains(cb, root.get(DevilFruitTypeVersionEntity.Fields.romaji), pattern);
			return cb.or(inRomaji, cb.exists(anyNameContaining(pattern, root, query.subquery(Integer.class), cb)));
		};
	}

	/** "This version has a translation whose name contains the text", as a subquery. */
	private static Subquery<Integer> anyNameContaining(String pattern, Root<DevilFruitTypeVersionEntity> root,
			Subquery<Integer> subquery, CriteriaBuilder cb) {
		Root<DevilFruitTypeVersionEntity> sameVersion = subquery.correlate(root);
		MapJoin<DevilFruitTypeVersionEntity, String, TranslationEmbeddable> translation = sameVersion
			.joinMap(DevilFruitTypeVersionEntity.Fields.translations);
		Predicate inName = contains(cb, translation.value().get(TranslationEmbeddable.Fields.name), pattern);
		return subquery.select(cb.literal(1)).where(inName);
	}

	private static Path<ContentVersionEntity> workflowOf(Root<DevilFruitTypeVersionEntity> root) {
		return root.get(DevilFruitTypeVersionEntity.Fields.version);
	}

	private static Predicate contains(CriteriaBuilder cb, Expression<String> field, String pattern) {
		return cb.like(cb.lower(field), pattern, LIKE_ESCAPE);
	}

	/** So that a "%" or "_" typed by the user is searched for, not interpreted. */
	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
