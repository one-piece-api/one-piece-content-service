package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * The name and description of an Affiliation in one language - a value owned by its
 * version (see {@link AffiliationVersionEntity#getTranslations()}).
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
public class AffiliationTranslationEmbeddable {

	private String name;

	private String description;

}
