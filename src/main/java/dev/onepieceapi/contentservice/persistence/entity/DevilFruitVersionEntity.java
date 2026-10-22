package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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

	/** Whether several characters may use it at the same time. */
	private boolean multipleUsers;

	/** The id of its image; null for none. */
	private String imageId;

	/** The content of the chapter it first appears in (ADR-0008); null for none. */
	private UUID firstAppearanceChapterId;

	/** The content of the episode it first appears in (ADR-0008); null for none. */
	private UUID firstAppearanceEpisodeId;

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

	@Override
	public NaturalKey naturalKey() {
		return new NaturalKey(Fields.romaji, this.romaji);
	}

	/** Replaces everything the version says, and notes when. */
	public void rewrite(DevilFruitFields fields, Instant now) {
		setRomaji(fields.romaji());
		this.typeContentId = fields.typeContentId();
		this.subcategoryId = fields.subcategoryId();
		this.imageId = fields.imageId();
		this.multipleUsers = fields.multipleUsers();
		this.firstAppearanceChapterId = fields.firstAppearance().chapterId();
		this.firstAppearanceEpisodeId = fields.firstAppearance().episodeId();
		this.translations.clear();
		this.translations.putAll(fields.translations());
		touch(now);
	}

}
