package dev.onepieceapi.contentservice.persistence.specification;

import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.UUID;

/**
 * The filters of the list of characters that are its own (implementation plan of the
 * Character, D16): the status, and a species its version names.
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

}
