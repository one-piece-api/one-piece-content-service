package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.character.RelativeKind;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.UUID;

/**
 * A relative of a Character - a value owned by its version (see
 * {@link CharacterVersionEntity#getRelatives()}).
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class CharacterRelativeEmbeddable {

	/** The content of the relative. */
	private UUID relativeContentId;

	@Enumerated(EnumType.STRING)
	private RelativeKind kind;

}
