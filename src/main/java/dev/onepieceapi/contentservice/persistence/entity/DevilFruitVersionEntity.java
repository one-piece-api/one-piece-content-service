package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What one version of a Devil Fruit says (implementation plan of the Devil Fruit, D1,
 * D7): its romaji (see {@link VersionBodyEntity}), the content of its Devil Fruit Type
 * and, per language, its name, description, advantages and disadvantages. The entity type
 * of the link is a constant the database fills in: it is what keeps a fruit from pointing
 * to a fruit.
 */
@Entity
@Table(name = "devil_fruit_version")
@Getter
@NoArgsConstructor
@FieldNameConstants
public class DevilFruitVersionEntity extends VersionBodyEntity {

	/** The type's content, not one of its versions; null while not chosen. */
	private UUID typeContentId;

	/**
	 * The localized fields per language code; a language nothing was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "devil_fruit_version_translation", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, TranslationEmbeddable> translations = new HashMap<>();

	public DevilFruitVersionEntity(ContentVersionEntity version) {
		super(version);
	}

	/** Replaces everything the version says, and notes when. */
	public void rewrite(String newRomaji, UUID newTypeContentId, Map<String, TranslationEmbeddable> newTranslations,
			Instant now) {
		setRomaji(newRomaji);
		this.typeContentId = newTypeContentId;
		this.translations.clear();
		this.translations.putAll(newTranslations);
		touch(now);
	}

}
