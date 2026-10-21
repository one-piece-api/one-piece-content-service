package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * What one version of an Affiliation says (docs/user-flows/content-editorial-workflow.md
 * 3.1.9): its romaji (its natural key), its kind, per language its name and description,
 * its emblem and its first appearance.
 */
@Entity
@Table(name = "affiliation_version")
@Getter
@NoArgsConstructor
@FieldNameConstants
public class AffiliationVersionEntity extends VersionBodyEntity {

	/**
	 * The natural key of the entity (see {@link #naturalKey()}); null while not written.
	 */
	private String romaji;

	/**
	 * The localized fields per language code; a language nothing was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "affiliation_version_translation", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, AffiliationTranslationEmbeddable> translations = new HashMap<>();

	/** The id of its image; null for none. */
	private String imageId;

	/** What sort of group it is; null while not chosen. */
	@Enumerated(EnumType.STRING)
	private AffiliationKind kind;

	/** The content of the chapter it first appears in (ADR-0008); null for none. */
	private UUID firstAppearanceChapterId;

	/** The content of the episode it first appears in (ADR-0008); null for none. */
	private UUID firstAppearanceEpisodeId;

	public AffiliationVersionEntity(ContentVersionEntity version) {
		super(version);
	}

	@Override
	public NaturalKey naturalKey() {
		return new NaturalKey(Fields.romaji, this.romaji);
	}

	/** Replaces everything the version says, and notes when. */
	public void rewrite(String newRomaji, Map<String, AffiliationTranslationEmbeddable> newTranslations,
			String newImageId, FirstAppearanceIds newFirstAppearance, AffiliationKind newKind, Instant now) {
		this.romaji = newRomaji;
		this.imageId = newImageId;
		this.kind = newKind;
		this.firstAppearanceChapterId = newFirstAppearance.chapterId();
		this.firstAppearanceEpisodeId = newFirstAppearance.episodeId();
		this.translations.clear();
		this.translations.putAll(newTranslations);
		touch(now);
	}

}
