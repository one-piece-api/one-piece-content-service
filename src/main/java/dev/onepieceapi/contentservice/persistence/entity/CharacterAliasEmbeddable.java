package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * An alias of a Character - a value owned by its version (see
 * {@link CharacterVersionEntity#getAliases()}).
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class CharacterAliasEmbeddable {

	@Column(name = "alias")
	private String name;

	/** Null when none of its points is chosen. */
	@Embedded
	private StoryWindowEmbeddable window;

}
