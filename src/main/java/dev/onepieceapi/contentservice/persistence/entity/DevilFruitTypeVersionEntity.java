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

/**
 * What one version of a Devil Fruit Type says
 * (docs/user-flows/content-editorial-workflow.md 3.1.1): its romaji (see
 * {@link VersionBodyEntity}) and, per language, its name, description, advantages and
 * disadvantages.
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

	public DevilFruitTypeVersionEntity(ContentVersionEntity version) {
		super(version);
	}

	/** Replaces everything the version says, and notes when. */
	public void rewrite(String newRomaji, Map<String, TranslationEmbeddable> newTranslations, Instant now) {
		setRomaji(newRomaji);
		this.translations.clear();
		this.translations.putAll(newTranslations);
		touch(now);
	}

}
