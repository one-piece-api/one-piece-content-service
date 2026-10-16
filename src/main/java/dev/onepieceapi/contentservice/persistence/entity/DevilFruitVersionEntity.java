package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
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
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What one version of a Devil Fruit says (implementation plan of the Devil Fruit, D1,
 * D7): its romaji (its natural key), the content of its Devil Fruit Type and of one of
 * its subcategories, and, per language, its name, description, advantages and
 * disadvantages. The entity type of the link is a constant the database fills in: it is
 * what keeps a fruit from pointing to a fruit.
 */
@Entity
@Table(name = "devil_fruit_version")
@Getter
@NoArgsConstructor
@FieldNameConstants
public class DevilFruitVersionEntity extends VersionBodyEntity {

	/**
	 * The natural key of the entity (see {@link #naturalKey()}); null while not written.
	 */
	@Setter
	private String romaji;

	/** The type's content, not one of its versions; null while not chosen. */
	private UUID typeContentId;

	/** One subcategory of that type; null for none. */
	private UUID subcategoryId;

	/** The id of its image; null for none. */
	private String imageId;

	/** The content of the chapter it first appears in (ADR-0008); null for none. */
	private UUID firstAppearanceChapterId;

	/**
	 * The localized fields per language code; a language nothing was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "devil_fruit_version_translation", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, TranslationEmbeddable> translations = new HashMap<>();

	/** Its reveals (ADR-0010), in the order of the story. */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "version_id", nullable = false)
	@OrderColumn(name = "position")
	private List<DevilFruitRevealEntity> reveals = new ArrayList<>();

	public DevilFruitVersionEntity(ContentVersionEntity version) {
		super(version);
	}

	@Override
	public Optional<NaturalKey> naturalKey() {
		return Optional.of(new NaturalKey(Fields.romaji, this.romaji));
	}

	/** Replaces everything the version says but its reveals, and notes when. */
	public void rewrite(String newRomaji, UUID newTypeContentId, UUID newSubcategoryId,
			Map<String, TranslationEmbeddable> newTranslations, String newImageId, UUID newFirstAppearanceChapterId,
			Instant now) {
		setRomaji(newRomaji);
		this.typeContentId = newTypeContentId;
		this.subcategoryId = newSubcategoryId;
		this.imageId = newImageId;
		this.firstAppearanceChapterId = newFirstAppearanceChapterId;
		this.translations.clear();
		this.translations.putAll(newTranslations);
		touch(now);
	}

	/**
	 * Replaces the reveals, each keeping its row by position, rewritten in place; those
	 * beyond the new ones are removed.
	 */
	public void rewriteReveals(List<DevilFruitRevealEntity.Written> newReveals) {
		for (int index = 0; index < newReveals.size(); index++) {
			if (index < this.reveals.size()) {
				this.reveals.get(index).rewrite(newReveals.get(index));
			}
			else {
				this.reveals.add(new DevilFruitRevealEntity(newReveals.get(index)));
			}
		}
		this.reveals.subList(newReveals.size(), this.reveals.size()).clear();
	}

}
