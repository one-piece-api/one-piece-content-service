package dev.onepieceapi.contentservice.persistence.specification;

import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.UserEmbeddable;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.UUID;

/**
 * The building blocks of the queries over the workflow of any kind of content - the
 * dashboard's status pages (UF-CNT-19) - following the Specification pattern, like
 * {@link VersionBodySpecifications} for an entity section.
 */
@UtilityClass
public class ContentVersionSpecifications {

	/**
	 * Keeps, for each content, only its highest-numbered version among {@code statuses}.
	 * One row per content, so counting the rows counts the contents.
	 */
	public Specification<ContentVersionEntity> mostRecentIn(Collection<VersionStatus> statuses) {
		return (root, query, cb) -> mostRecentIn(root, statuses, query, cb);
	}

	/**
	 * "This version is its content's highest-numbered one among {@code statuses}", for a
	 * version reached by any path - shared with the lists of each kind of content.
	 */
	static Predicate mostRecentIn(Path<ContentVersionEntity> version, Collection<VersionStatus> statuses,
			CommonAbstractCriteria query, CriteriaBuilder cb) {
		Subquery<Integer> mostRecent = query.subquery(Integer.class);
		Root<ContentVersionEntity> other = mostRecent.from(ContentVersionEntity.class);
		Predicate sameContent = cb.equal(other.get(ContentVersionEntity.Fields.contentId),
				version.get(ContentVersionEntity.Fields.contentId));
		Predicate eligible = other.get(ContentVersionEntity.Fields.status).in(statuses);
		mostRecent.select(cb.max(other.<Integer>get(ContentVersionEntity.Fields.versionNumber)))
			.where(sameContent, eligible);
		return cb.equal(version.get(ContentVersionEntity.Fields.versionNumber), mostRecent);
	}

	/** Only the versions of contents of this kind. */
	public Specification<ContentVersionEntity> ofEntityType(EntityType entityType) {
		return (root, query, cb) -> {
			Subquery<Integer> ofKind = query.subquery(Integer.class);
			Root<ContentEntity> content = ofKind.from(ContentEntity.class);
			ofKind.select(cb.literal(1))
				.where(cb.equal(content.get(ContentEntity.Fields.id), root.get(ContentVersionEntity.Fields.contentId)),
						cb.equal(content.get(ContentEntity.Fields.entityType), entityType));
			return cb.exists(ofKind);
		};
	}

	public Specification<ContentVersionEntity> authoredBy(String username) {
		return (root, query, cb) -> cb
			.equal(root.get(ContentVersionEntity.Fields.author).get(UserEmbeddable.Fields.username), username);
	}

	public Specification<ContentVersionEntity> authoredByUser(UUID userId) {
		return (root, query, cb) -> cb
			.equal(root.get(ContentVersionEntity.Fields.author).get(UserEmbeddable.Fields.userId), userId);
	}

	public Specification<ContentVersionEntity> claimedBy(UUID userId) {
		return (root, query, cb) -> cb
			.equal(root.get(ContentVersionEntity.Fields.claimant).get(UserEmbeddable.Fields.userId), userId);
	}

}
