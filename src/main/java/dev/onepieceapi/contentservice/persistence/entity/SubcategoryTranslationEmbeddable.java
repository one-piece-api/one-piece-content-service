package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The name and description of a subcategory in one language - a value owned by its
 * subcategory (see {@link DevilFruitTypeSubcategoryEntity#getTranslations()}).
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SubcategoryTranslationEmbeddable {

	private String name;

	private String description;

}
