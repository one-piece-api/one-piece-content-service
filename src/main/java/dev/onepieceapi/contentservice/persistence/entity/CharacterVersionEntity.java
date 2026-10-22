package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.character.Gender;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What one version of a Character says (docs/user-flows/content-editorial-workflow.md
 * 3.1.11): its romaji (its natural key), per language its name and description, its
 * closed values, its lists in order (implementation plan of the Character, D14), its
 * portrait and its first appearance.
 */
@Entity
@Table(name = "character_version")
@Getter
@NoArgsConstructor
@FieldNameConstants
public class CharacterVersionEntity extends VersionBodyEntity {

	/**
	 * The natural key of the entity (see {@link #naturalKey()}); null while not written.
	 */
	private String romaji;

	private String japaneseName;

	/**
	 * The localized fields per language code; a language nothing was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "character_version_translation", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, CharacterTranslationEmbeddable> translations = new HashMap<>();

	@Enumerated(EnumType.STRING)
	private CharacterStatus status;

	@Enumerated(EnumType.STRING)
	private Gender gender;

	/** Null together with {@link #birthdayDay} for no birthday. */
	private Short birthdayMonth;

	private Short birthdayDay;

	/** In the order they are shown; the position is the index in the list. */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "version_id", nullable = false)
	@OrderColumn(name = "position")
	private List<CharacterEpithetEntity> epithets = new ArrayList<>();

	/** In the order they are shown; the position is the index in the list. */
	@ElementCollection
	@CollectionTable(name = "character_version_alias", joinColumns = @JoinColumn(name = "version_id"))
	@OrderColumn(name = "position")
	@Column(name = "alias")
	private List<String> aliases = new ArrayList<>();

	/** The content of each species, in the order they are shown. */
	@ElementCollection
	@CollectionTable(name = "character_version_species", joinColumns = @JoinColumn(name = "version_id"))
	@OrderColumn(name = "position")
	@Column(name = "species_content_id")
	private List<UUID> speciesIds = new ArrayList<>();

	/** In the order they are shown; the position is the index in the list. */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "version_id", nullable = false)
	@OrderColumn(name = "position")
	private List<CharacterMembershipEntity> affiliations = new ArrayList<>();

	/** The id of its image; null for none. */
	private String imageId;

	/** The content of the chapter it first appears in (ADR-0008); null for none. */
	private UUID firstAppearanceChapterId;

	/** The content of the episode it first appears in (ADR-0008); null for none. */
	private UUID firstAppearanceEpisodeId;

	public CharacterVersionEntity(ContentVersionEntity version) {
		super(version);
	}

	@Override
	public NaturalKey naturalKey() {
		return new NaturalKey(Fields.romaji, this.romaji);
	}

	/** Replaces everything the version says, and notes when. */
	public void rewrite(CharacterFields fields, Instant now) {
		this.romaji = fields.romaji();
		this.japaneseName = fields.japaneseName();
		this.status = fields.status();
		this.gender = fields.gender();
		this.birthdayMonth = fields.birthdayMonth();
		this.birthdayDay = fields.birthdayDay();
		this.imageId = fields.imageId();
		this.firstAppearanceChapterId = fields.firstAppearance().chapterId();
		this.firstAppearanceEpisodeId = fields.firstAppearance().episodeId();
		this.translations.clear();
		this.translations.putAll(fields.translations());
		rewriteEpithets(fields.epithets());
		this.aliases.clear();
		this.aliases.addAll(fields.aliases());
		this.speciesIds.clear();
		this.speciesIds.addAll(fields.speciesIds());
		rewriteAffiliations(fields.affiliations());
		touch(now);
	}

	/** As the epithets: each position keeps its row, only the end of the list changes. */
	private void rewriteAffiliations(List<Membership> written) {
		for (int index = 0; index < written.size(); index++) {
			if (index < this.affiliations.size()) {
				this.affiliations.get(index).rewrite(written.get(index));
			}
			else {
				this.affiliations.add(new CharacterMembershipEntity(written.get(index)));
			}
		}
		while (this.affiliations.size() > written.size()) {
			this.affiliations.removeLast();
		}
	}

	/**
	 * Each position keeps its row, rewritten in place; only the end of the list grows or
	 * shrinks. Replacing the rows would insert a position before the old row holding it
	 * is deleted.
	 */
	private void rewriteEpithets(List<Map<String, String>> written) {
		for (int index = 0; index < written.size(); index++) {
			if (index < this.epithets.size()) {
				this.epithets.get(index).rewrite(written.get(index));
			}
			else {
				this.epithets.add(new CharacterEpithetEntity(written.get(index)));
			}
		}
		while (this.epithets.size() > written.size()) {
			this.epithets.removeLast();
		}
	}

}
