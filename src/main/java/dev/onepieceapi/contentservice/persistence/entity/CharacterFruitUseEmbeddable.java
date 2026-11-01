package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.UUID;

/**
 * A Devil Fruit of a Character - a value owned by its version (see
 * {@link CharacterVersionEntity#getDevilFruits()}).
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class CharacterFruitUseEmbeddable {

	/** The content of the fruit. */
	private UUID devilFruitContentId;

	/** Null when none of its points is chosen. */
	@Embedded
	private StoryWindowEmbeddable window;

}
