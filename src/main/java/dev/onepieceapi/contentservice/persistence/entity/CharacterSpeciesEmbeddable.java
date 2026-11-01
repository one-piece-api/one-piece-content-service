package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.UUID;

/**
 * A species of a Character - a value owned by its version (see
 * {@link CharacterVersionEntity#getSpecies()}).
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class CharacterSpeciesEmbeddable {

	/** The content of the species. */
	private UUID speciesContentId;

	/** Null when none of its points is chosen. */
	@Embedded
	private StoryWindowEmbeddable window;

}
