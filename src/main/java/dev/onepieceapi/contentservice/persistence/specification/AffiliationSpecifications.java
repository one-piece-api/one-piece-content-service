package dev.onepieceapi.contentservice.persistence.specification;

import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationVersionEntity;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

/**
 * The filters of the list of affiliations that are its own (implementation plan of the
 * Character, D16): the kind.
 */
@UtilityClass
public class AffiliationSpecifications {

	public Specification<AffiliationVersionEntity> withKind(AffiliationKind kind) {
		return (root, query, cb) -> cb.equal(root.get(AffiliationVersionEntity.Fields.kind), kind);
	}

}
