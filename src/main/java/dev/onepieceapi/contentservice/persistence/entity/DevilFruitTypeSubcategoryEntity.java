package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A subcategory of one version of a Devil Fruit Type (implementation plan of the
 * subcategories, S1): a child of {@link DevilFruitTypeVersionEntity}, saved and removed
 * only through it. Its row has an id of its own; the subcategory id is the one every
 * version of the type that keeps it shares.
 */
@Entity
@Table(name = "devil_fruit_type_version_subcategory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DevilFruitTypeSubcategoryEntity {

	@Id
	private UUID id;

	private UUID subcategoryId;

	/**
	 * The localized fields per language code; a language nothing was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "devil_fruit_type_version_subcategory_translation",
			joinColumns = @JoinColumn(name = "subcategory_row_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, SubcategoryTranslationEmbeddable> translations = new HashMap<>();

	public DevilFruitTypeSubcategoryEntity(UUID subcategoryId,
			Map<String, SubcategoryTranslationEmbeddable> translations) {
		this.id = UUID.randomUUID();
		this.subcategoryId = subcategoryId;
		rewrite(translations);
	}

	/** Replaces what the subcategory says. */
	public void rewrite(Map<String, SubcategoryTranslationEmbeddable> newTranslations) {
		this.translations.clear();
		this.translations.putAll(newTranslations);
	}

}
