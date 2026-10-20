package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * The description of a bounty version in one language - a value owned by its version,
 * with no identity of its own. A bounty has no localized name.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class BountyTranslationEmbeddable {

	private String description;

}
