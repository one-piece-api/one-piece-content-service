package dev.onepieceapi.contentservice.persistence.entity;

import java.util.List;
import java.util.ArrayList;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
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
 * What one version of a Species says (docs/user-flows/content-editorial-workflow.md
 * 3.1.10): its romaji (its natural key), per language its name, its picture, its first
 * appearance and its paragraphs.
 */
@Entity
@Table(name = "species_version")
@Getter
@NoArgsConstructor
@FieldNameConstants
public class SpeciesVersionEntity extends VersionBodyEntity {

	/**
	 * The natural key of the entity (see {@link #naturalKey()}); null while not written.
	 */
	private String romaji;

	/**
	 * The localized fields per language code; a language nothing was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "species_version_translation", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, SpeciesTranslationEmbeddable> translations = new HashMap<>();

	/**
	 * Its paragraphs, every section in one list (workflow 3.7.3): a section is in the
	 * order of its rows; the position is the index in the list.
	 */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "version_id", nullable = false)
	@OrderColumn(name = "position")
	private List<SpeciesParagraphEntity> paragraphs = new ArrayList<>();

	/** The id of its image; null for none. */
	private String imageId;

	/** The content of the chapter it first appears in (ADR-0008); null for none. */
	private UUID firstAppearanceChapterId;

	/** The content of the episode it first appears in (ADR-0008); null for none. */
	private UUID firstAppearanceEpisodeId;

	public SpeciesVersionEntity(ContentVersionEntity version) {
		super(version);
	}

	@Override
	public NaturalKey naturalKey() {
		return new NaturalKey(Fields.romaji, this.romaji);
	}

	/** Replaces everything the version says, and notes when. */
	public void rewrite(String newRomaji, Map<String, SpeciesTranslationEmbeddable> newTranslations, String newImageId,
			FirstAppearanceIds newFirstAppearance, List<ParagraphFields> newParagraphs, Instant now) {
		this.romaji = newRomaji;
		this.imageId = newImageId;
		this.firstAppearanceChapterId = newFirstAppearance.chapterId();
		this.firstAppearanceEpisodeId = newFirstAppearance.episodeId();
		this.translations.clear();
		this.translations.putAll(newTranslations);
		PositionalRows.rewrite(this.paragraphs, newParagraphs, SpeciesParagraphEntity::new, ParagraphEntity::rewrite);
		touch(now);
	}

}
