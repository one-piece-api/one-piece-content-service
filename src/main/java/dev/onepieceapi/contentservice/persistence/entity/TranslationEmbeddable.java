package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * The name, description, advantages and disadvantages of a version in one language - a
 * value owned by its version, with no identity of its own (see
 * {@link DevilFruitTypeVersionEntity#getTranslations()}).
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class TranslationEmbeddable {

	private String name;

	private String description;

	private String advantages;

	private String disadvantages;

}
