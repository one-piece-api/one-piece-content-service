package dev.onepieceapi.contentservice.persistence.specification;

import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterMembershipEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.UUID;

/**
 * The filters of the list of characters that are its own (implementation plan of the
 * Character, D16): the status, a species and an affiliation its version names, and the
 * kind of an affiliation it currently belongs to.
 */
@UtilityClass
public class CharacterSpecifications {

	public Specification<CharacterVersionEntity> withStatus(CharacterStatus status) {
		return (root, query, cb) -> cb.equal(root.get(CharacterVersionEntity.Fields.status), status);
	}

	/** The characters whose version names this species (its content id) in its list. */
	public Specification<CharacterVersionEntity> withSpecies(UUID speciesContentId) {
		return (root, query, cb) -> cb.isMember(speciesContentId,
				root.<Collection<UUID>>get(CharacterVersionEntity.Fields.speciesIds));
	}

	/**
	 * The characters whose version lists this affiliation (its content id) as one they
	 * belong to: a membership marked {@code former} does not count.
	 */
	public Specification<CharacterVersionEntity> withAffiliation(UUID affiliationContentId) {
		return (root, query, cb) -> {
			Subquery<Integer> member = query.subquery(Integer.class);
			Join<CharacterVersionEntity, CharacterMembershipEntity> membership = membershipsOf(root, member);
			member.select(cb.literal(1))
				.where(cb.equal(membership.get(CharacterMembershipEntity.Fields.affiliationContentId),
						affiliationContentId), currentOnly(membership, cb));
			return cb.exists(member);
		};
	}

	/**
	 * The characters whose version lists an affiliation of this kind as one they belong
	 * to, the kind being that of the affiliation's latest approved version - the one a
	 * save checks a rank against. A former membership does not count.
	 */
	public Specification<CharacterVersionEntity> withAffiliationKind(AffiliationKind kind) {
		return (root, query, cb) -> {
			Subquery<Integer> member = query.subquery(Integer.class);
			Join<CharacterVersionEntity, CharacterMembershipEntity> membership = membershipsOf(root, member);
			Root<AffiliationVersionEntity> affiliation = member.from(AffiliationVersionEntity.class);
			Join<AffiliationVersionEntity, ContentVersionEntity> version = affiliation
				.join(VersionBodyEntity.Fields.version);
			member.select(cb.literal(1))
				.where(currentOnly(membership, cb),
						cb.equal(membership.get(CharacterMembershipEntity.Fields.affiliationContentId),
								version.get(ContentVersionEntity.Fields.contentId)),
						cb.equal(affiliation.get(AffiliationVersionEntity.Fields.kind), kind),
						ContentVersionSpecifications.mostRecentIn(version, VersionStatus.approved(), member, cb));
			return cb.exists(member);
		};
	}

	/** The memberships of the character the outer query is on. */
	private static Join<CharacterVersionEntity, CharacterMembershipEntity> membershipsOf(
			Root<CharacterVersionEntity> root, Subquery<Integer> subquery) {
		return subquery.correlate(root).join(CharacterVersionEntity.Fields.affiliations);
	}

	private static Predicate currentOnly(Join<CharacterVersionEntity, CharacterMembershipEntity> membership,
			CriteriaBuilder cb) {
		return cb.isFalse(membership.get(CharacterMembershipEntity.Fields.former));
	}

}
