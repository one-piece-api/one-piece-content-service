package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What one version of a chapter says (implementation plan of the story structure, S2, S4,
 * S13): its number (its natural key), the content of its arc and of its volume, the
 * Japanese title, the page count, the release day per country and, per language, its
 * title and description. The entity types of the links are constants the database fills
 * in: they keep a chapter from pointing to anything but an arc and a volume.
 */
@Entity
@Table(name = "chapter_version")
@Getter
@NoArgsConstructor
@FieldNameConstants
public class ChapterVersionEntity extends VersionBodyEntity {

	/** The natural key of the entity; null while not written. */
	private Integer number;

	/** The arc's content, not one of its versions; null while not chosen. */
	private UUID arcContentId;

	/** The volume's content; null for none. */
	private UUID volumeContentId;

	private String japaneseTitle;

	private Integer pageCount;

	/** The release day by country code. */
	@ElementCollection
	@CollectionTable(name = "chapter_version_release", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "country")
	@Column(name = "release_date")
	private Map<String, LocalDate> releases = new HashMap<>();

	/**
	 * The localized fields per language code; a language nothing was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "chapter_version_translation", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, StoryTranslationEmbeddable> translations = new HashMap<>();

	public ChapterVersionEntity(ContentVersionEntity version) {
		super(version);
	}

	@Override
	public NaturalKey naturalKey() {
		return new NaturalKey(Fields.number, this.number);
	}

	/** Replaces everything the version says; the caller notes when ({@link #touch}). */
	public void rewrite(Integer newNumber, UUID newArcContentId, UUID newVolumeContentId, String newJapaneseTitle,
			Integer newPageCount, Map<String, LocalDate> newReleases,
			Map<String, StoryTranslationEmbeddable> newTranslations) {
		this.number = newNumber;
		this.arcContentId = newArcContentId;
		this.volumeContentId = newVolumeContentId;
		this.japaneseTitle = newJapaneseTitle;
		this.pageCount = newPageCount;
		this.releases.clear();
		this.releases.putAll(newReleases);
		this.translations.clear();
		this.translations.putAll(newTranslations);
	}

}
