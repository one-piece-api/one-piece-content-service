package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What one version of a Devil Fruit Type says
 * (docs/user-flows/content-editorial-workflow.md 3.1.1): its romaji (see
 * {@link VersionBodyEntity}), per language its name, description, advantages and
 * disadvantages, and its subcategories in order.
 */
@Entity
@Table(name = "devil_fruit_type_version")
@Getter
@NoArgsConstructor
@FieldNameConstants
public class DevilFruitTypeVersionEntity extends VersionBodyEntity {

	/**
	 * The localized fields per language code; a language nothing was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "devil_fruit_type_version_translation", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, TranslationEmbeddable> translations = new HashMap<>();

	/** In the order they are shown; the position is the index in the list. */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "version_id", nullable = false)
	@OrderColumn(name = "position")
	private List<DevilFruitTypeSubcategoryEntity> subcategories = new ArrayList<>();

	public DevilFruitTypeVersionEntity(ContentVersionEntity version) {
		super(version);
	}

	/**
	 * Replaces everything the version says, and notes when. A subcategory kept keeps its
	 * row, rewritten in place: removing it and adding it again would insert its id before
	 * the old row is deleted.
	 * @param newSubcategories the translations of each subcategory by its id, in order
	 */
	public void rewrite(String newRomaji, Map<String, TranslationEmbeddable> newTranslations,
			Map<UUID, Map<String, SubcategoryTranslationEmbeddable>> newSubcategories, Instant now) {
		setRomaji(newRomaji);
		this.translations.clear();
		this.translations.putAll(newTranslations);
		Map<UUID, DevilFruitTypeSubcategoryEntity> kept = this.subcategories.stream()
			.collect(Collectors.toMap(DevilFruitTypeSubcategoryEntity::getSubcategoryId, Function.identity()));
		List<DevilFruitTypeSubcategoryEntity> ordered = new ArrayList<>();
		newSubcategories.forEach((subcategoryId, subcategoryTranslations) -> {
			DevilFruitTypeSubcategoryEntity row = kept.get(subcategoryId);
			if (row == null) {
				ordered.add(new DevilFruitTypeSubcategoryEntity(subcategoryId, subcategoryTranslations));
			}
			else {
				row.rewrite(subcategoryTranslations);
				ordered.add(row);
			}
		});
		this.subcategories.clear();
		this.subcategories.addAll(ordered);
		touch(now);
	}

}
