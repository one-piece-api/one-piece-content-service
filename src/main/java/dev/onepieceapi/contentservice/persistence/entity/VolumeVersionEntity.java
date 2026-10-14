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

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * What one version of a volume says (implementation plan of the story structure, S2, S6,
 * S13): its number (its natural key), the Japanese title, its cover, the release day per
 * country and, per language, its title.
 */
@Entity
@Table(name = "volume_version")
@Getter
@NoArgsConstructor
@FieldNameConstants
public class VolumeVersionEntity extends VersionBodyEntity {

	/** The natural key of the entity; null while not written. */
	private Integer number;

	private String japaneseTitle;

	/** The id of its cover image; null for none. */
	private String coverImageId;

	/** The release day by country code. */
	@ElementCollection
	@CollectionTable(name = "volume_version_release", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "country")
	@Column(name = "release_date")
	private Map<String, LocalDate> releases = new HashMap<>();

	/**
	 * The localized fields per language code; a language nothing was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "volume_version_translation", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, VolumeTranslationEmbeddable> translations = new HashMap<>();

	public VolumeVersionEntity(ContentVersionEntity version) {
		super(version);
	}

	@Override
	public NaturalKey naturalKey() {
		return new NaturalKey(Fields.number, this.number);
	}

	/** Replaces everything the version says, and notes when. */
	public void rewrite(Integer newNumber, String newJapaneseTitle, String newCoverImageId,
			Map<String, LocalDate> newReleases, Map<String, VolumeTranslationEmbeddable> newTranslations, Instant now) {
		this.number = newNumber;
		this.japaneseTitle = newJapaneseTitle;
		this.coverImageId = newCoverImageId;
		this.releases.clear();
		this.releases.putAll(newReleases);
		this.translations.clear();
		this.translations.putAll(newTranslations);
		touch(now);
	}

}
