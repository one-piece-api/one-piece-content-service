package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What one version of a Devil Fruit Type says
 * (docs/user-flows/content-editorial-workflow.md 3.1): its romaji and, per language, its
 * name and description. It shares its id with the {@link ContentVersionEntity} carrying
 * the workflow of that version, and is saved and removed together with it.
 */
@Entity
@Table(name = "devil_fruit_type_version")
@Getter
@NoArgsConstructor
@RequiredArgsConstructor
@FieldNameConstants
public class DevilFruitTypeVersionEntity {

	@Id
	private UUID versionId;

	@NonNull
	@OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@MapsId
	@JoinColumn(name = "version_id")
	private ContentVersionEntity version;

	@Setter
	private String romaji;

	/**
	 * Name and description per language code; a language nothing was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "devil_fruit_type_version_translation", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, TranslationEmbeddable> translations = new HashMap<>();

	/** The content this version belongs to. */
	public UUID getContentId() {
		return this.version.getContentId();
	}

	/** Replaces everything the version says, and notes when. */
	public void rewrite(String newRomaji, Map<String, TranslationEmbeddable> newTranslations, Instant now) {
		this.romaji = newRomaji;
		this.translations.clear();
		this.translations.putAll(newTranslations);
		this.version.setUpdatedAt(now);
	}

	/** Takes the version to another status, and notes when. */
	public void moveTo(VersionStatus newStatus, Instant now) {
		this.version.moveTo(newStatus, now);
	}

	/** A reviewer takes the version. */
	public void claimBy(UserEmbeddable reviewer) {
		this.version.claimBy(reviewer);
	}

	/** Nobody holds the version any more. */
	public void release() {
		this.version.release();
	}

}
