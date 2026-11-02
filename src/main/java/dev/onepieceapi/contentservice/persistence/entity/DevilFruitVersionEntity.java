package dev.onepieceapi.contentservice.persistence.entity;

import java.util.List;
import java.util.ArrayList;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;

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
 * disadvantages. Its initial knowledge (workflow 3.7.1) is a row of its own, written only
 * when it holds something: an optional secondary table, which Hibernate leaves out while
 * every column is null. The entity type of the link is a constant the database fills in:
 * it is what keeps a fruit from pointing to a fruit.
 */
@Entity
@Table(name = "devil_fruit_version")
@SecondaryTable(name = "devil_fruit_version_initial_knowledge",
		pkJoinColumns = @PrimaryKeyJoinColumn(name = "version_id"))
@Getter
@NoArgsConstructor
@FieldNameConstants
public class DevilFruitVersionEntity extends VersionBodyEntity {

	private static final String INITIAL_KNOWLEDGE = "devil_fruit_version_initial_knowledge";

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

	/**
	 * Its paragraphs, every section in one list (workflow 3.7.3): a section is in the
	 * order of its rows; the position is the index in the list.
	 */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "version_id", nullable = false)
	@OrderColumn(name = "position")
	private List<DevilFruitParagraphEntity> paragraphs = new ArrayList<>();

	/** The values known at the first appearance; null when not part of it. */
	@Column(table = INITIAL_KNOWLEDGE, name = "romaji")
	private String initialRomaji;

	@Column(table = INITIAL_KNOWLEDGE, name = "type_content_id")
	private UUID initialTypeContentId;

	@Column(table = INITIAL_KNOWLEDGE, name = "subcategory_id")
	private UUID initialSubcategoryId;

	/** The names known at the first appearance, per language code. */
	@ElementCollection
	@CollectionTable(name = "devil_fruit_version_initial_knowledge_translation",
			joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, TranslationEmbeddable> initialTranslations = new HashMap<>();

	/** Its reveals (workflow 3.7.1), in list order. */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "version_id", nullable = false)
	@OrderColumn(name = "position")
	private List<DevilFruitRevealEntity> reveals = new ArrayList<>();

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
		PositionalRows.rewrite(this.paragraphs, fields.paragraphs(), DevilFruitParagraphEntity::new,
				ParagraphEntity::rewrite);
		this.initialRomaji = fields.initialRomaji();
		this.initialTypeContentId = fields.initialTypeContentId();
		this.initialSubcategoryId = fields.initialSubcategoryId();
		this.initialTranslations.clear();
		this.initialTranslations.putAll(fields.initialTranslations());
		PositionalRows.rewrite(this.reveals, fields.reveals(), DevilFruitRevealEntity::new,
				DevilFruitRevealEntity::rewrite);
		touch(now);
	}

}
