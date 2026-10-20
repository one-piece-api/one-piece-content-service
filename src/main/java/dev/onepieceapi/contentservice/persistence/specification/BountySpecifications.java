package dev.onepieceapi.contentservice.persistence.specification;

import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import dev.onepieceapi.contentservice.domain.bounty.BountyValidity;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.BountyVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

/**
 * The filters of the list of bounties that are its own: the condition, and the validity
 * (implementation plan of the Bounty, D3, D5), which is derived and so asked as a
 * question about the other bounties, never read from a column.
 */
@UtilityClass
public class BountySpecifications {

	public Specification<BountyVersionEntity> withCondition(BountyCondition condition) {
		return (root, query, cb) -> cb.equal(root.get(BountyVersionEntity.Fields.condition), condition);
	}

	/**
	 * The bounties whose row shows this validity: inactive, or - not inactive - replaced
	 * or not by another bounty whose most recent version among {@code statuses} names it
	 * as its previous. The statuses are the caller's, so an editor and a reader may be
	 * told different things.
	 */
	public Specification<BountyVersionEntity> withValidity(BountyValidity validity,
			Collection<VersionStatus> statuses) {
		return (root, query, cb) -> {
			Predicate inactive = cb.isTrue(root.get(BountyVersionEntity.Fields.inactive));
			return switch (validity) {
				case INACTIVE -> inactive;
				case SUPERSEDED -> cb.and(cb.not(inactive), replaced(root, query, cb, statuses));
				case ACTIVE -> cb.and(cb.not(inactive), cb.not(replaced(root, query, cb, statuses)));
			};
		};
	}

	/**
	 * "Another bounty replaces this content": an {@code exists} over the bounties whose
	 * most recent version among the statuses has this content as its previous.
	 */
	private static Predicate replaced(Root<BountyVersionEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb,
			Collection<VersionStatus> statuses) {
		Subquery<Integer> replacing = query.subquery(Integer.class);
		Root<BountyVersionEntity> other = replacing.from(BountyVersionEntity.class);
		Join<BountyVersionEntity, ContentVersionEntity> otherVersion = other.join(VersionBodyEntity.Fields.version);
		Predicate ownContent = cb.equal(other.get(BountyVersionEntity.Fields.previousContentId),
				root.get(VersionBodyEntity.Fields.version).get(ContentVersionEntity.Fields.contentId));
		replacing.select(cb.literal(1))
			.where(ownContent, ContentVersionSpecifications.mostRecentIn(otherVersion, statuses, replacing, cb));
		return cb.exists(replacing);
	}

}
