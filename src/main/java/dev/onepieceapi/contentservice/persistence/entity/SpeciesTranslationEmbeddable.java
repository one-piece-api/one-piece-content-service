package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * The name and description of a Species in one language - a value owned by its version
 * (see {@link SpeciesVersionEntity#getTranslations()}).
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class SpeciesTranslationEmbeddable {

	private String name;

	private String description;

}
