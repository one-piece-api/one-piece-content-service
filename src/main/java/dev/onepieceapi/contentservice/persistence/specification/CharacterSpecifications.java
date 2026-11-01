package dev.onepieceapi.contentservice.persistence.specification;

import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterFruitUseEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.CharacterMembershipEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterMembershipPeriodEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterSpeciesEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.StoryWindowEmbeddable;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/**
 * The filters of the list of characters that are its own (implementation plan of the
 * Character, D16): the status, a species and an affiliation its version names, the kind
 * of an affiliation it currently belongs to, and a Devil Fruit it currently has. Current,
 * without a filter on the reader axis, is a window without an end (workflow 3.7.2): a
 * membership is current while one of its periods is (3.4.7).
 */
@UtilityClass
public class CharacterSpecifications {

	public Specification<CharacterVersionEntity> withStatus(CharacterStatus status) {
		return (root, query, cb) -> cb.equal(root.get(CharacterVersionEntity.Fields.status), status);
	}

	/** The characters whose version names this species (its content id) in its list. */
	public Specification<CharacterVersionEntity> withSpecies(UUID speciesContentId) {
		return (root, query, cb) -> {
			Subquery<Integer> named = query.subquery(Integer.class);
			Join<CharacterVersionEntity, CharacterSpeciesEmbeddable> species = named.correlate(root)
				.join(CharacterVersionEntity.Fields.species);
			named.select(cb.literal(1))
				.where(cb.equal(species.get(CharacterSpeciesEmbeddable.Fields.speciesContentId), speciesContentId));
			return cb.exists(named);
		};
	}

	/**
	 * The characters whose version lists this affiliation (its content id) as one they
	 * currently belong to.
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
	 * The characters whose version lists an affiliation of this kind as one they
	 * currently belong to, the kind being that of the affiliation's latest approved
	 * version - the one a save checks a rank against.
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

	/**
	 * The characters whose version lists this Devil Fruit (its content id) as one they
	 * currently have.
	 */
	public Specification<CharacterVersionEntity> withDevilFruit(UUID devilFruitContentId) {
		return (root, query, cb) -> {
			Subquery<Integer> user = query.subquery(Integer.class);
			Join<CharacterVersionEntity, CharacterFruitUseEmbeddable> use = user.correlate(root)
				.join(CharacterVersionEntity.Fields.devilFruits);
			user.select(cb.literal(1))
				.where(cb.equal(use.get(CharacterFruitUseEmbeddable.Fields.devilFruitContentId), devilFruitContentId),
						open(use, CharacterFruitUseEmbeddable.Fields.window, cb));
			return cb.exists(user);
		};
	}

	/** The memberships of the character the outer query is on. */
	private static Join<CharacterVersionEntity, CharacterMembershipEntity> membershipsOf(
			Root<CharacterVersionEntity> root, Subquery<Integer> subquery) {
		return subquery.correlate(root).join(CharacterVersionEntity.Fields.affiliations);
	}

	/** A membership with a period without an end. */
	private static Predicate currentOnly(Join<CharacterVersionEntity, CharacterMembershipEntity> membership,
			CriteriaBuilder cb) {
		Join<CharacterMembershipEntity, CharacterMembershipPeriodEntity> period = membership
			.join(CharacterMembershipEntity.Fields.periods);
		return open(period, CharacterMembershipPeriodEntity.Fields.window, cb);
	}

	/** The window held by this attribute has no end on any axis. */
	private static Predicate open(From<?, ?> holder, String windowAttribute, CriteriaBuilder cb) {
		var window = holder.get(windowAttribute);
		return cb.and(cb.isNull(window.get(StoryWindowEmbeddable.Fields.endedChapterId)),
				cb.isNull(window.get(StoryWindowEmbeddable.Fields.endedEpisodeId)));
	}

}
