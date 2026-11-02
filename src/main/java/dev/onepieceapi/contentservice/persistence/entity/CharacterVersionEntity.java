package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.character.Gender;
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
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;

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
 * gallery (workflow 3.5.1) and its first appearance, its initial knowledge (workflow
 * 3.7.1: an optional secondary table, no row while every column is null) and its reveals.
 */
@Entity
@Table(name = "character_version")
@SecondaryTable(name = "character_version_initial_knowledge",
		pkJoinColumns = @PrimaryKeyJoinColumn(name = "version_id"))
@Getter
@NoArgsConstructor
@FieldNameConstants
public class CharacterVersionEntity extends VersionBodyEntity {

	private static final String INITIAL_KNOWLEDGE = "character_version_initial_knowledge";

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
	private List<CharacterAliasEmbeddable> aliases = new ArrayList<>();

	/** Its species, in the order they are shown. */
	@ElementCollection
	@CollectionTable(name = "character_version_species", joinColumns = @JoinColumn(name = "version_id"))
	@OrderColumn(name = "position")
	private List<CharacterSpeciesEmbeddable> species = new ArrayList<>();

	/** In the order they are shown; the position is the index in the list. */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "version_id", nullable = false)
	@OrderColumn(name = "position")
	private List<CharacterMembershipEntity> affiliations = new ArrayList<>();

	/** The fruits it ate, in the order they are shown. */
	@ElementCollection
	@CollectionTable(name = "character_version_devil_fruit", joinColumns = @JoinColumn(name = "version_id"))
	@OrderColumn(name = "position")
	private List<CharacterFruitUseEmbeddable> devilFruits = new ArrayList<>();

	/** Its relatives, in the order they are shown. */
	@ElementCollection
	@CollectionTable(name = "character_version_relative", joinColumns = @JoinColumn(name = "version_id"))
	@OrderColumn(name = "position")
	private List<CharacterRelativeEmbeddable> relatives = new ArrayList<>();

	/**
	 * Its paragraphs, every section in one list (workflow 3.7.3): a section is in the
	 * order of its rows; the position is the index in the list.
	 */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "version_id", nullable = false)
	@OrderColumn(name = "position")
	private List<CharacterParagraphEntity> paragraphs = new ArrayList<>();

	/** Its gallery (workflow 3.5.1), in the order shown; the position is the index. */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "version_id", nullable = false)
	@OrderColumn(name = "position")
	private List<CharacterImageEntity> gallery = new ArrayList<>();

	/** The content of the chapter it first appears in (ADR-0008); null for none. */
	private UUID firstAppearanceChapterId;

	/** The content of the episode it first appears in (ADR-0008); null for none. */
	private UUID firstAppearanceEpisodeId;

	/** The values known at the first appearance; null when not part of it. */
	@Column(table = INITIAL_KNOWLEDGE, name = "romaji")
	private String initialRomaji;

	@Column(table = INITIAL_KNOWLEDGE, name = "japanese_name")
	private String initialJapaneseName;

	@Column(table = INITIAL_KNOWLEDGE, name = "status")
	@Enumerated(EnumType.STRING)
	private CharacterStatus initialStatus;

	/** The names known at the first appearance, per language code. */
	@ElementCollection
	@CollectionTable(name = "character_version_initial_knowledge_translation",
			joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, CharacterTranslationEmbeddable> initialTranslations = new HashMap<>();

	/** Its reveals (workflow 3.7.1), in list order. */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "version_id", nullable = false)
	@OrderColumn(name = "position")
	private List<CharacterRevealEntity> reveals = new ArrayList<>();

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
		this.firstAppearanceChapterId = fields.firstAppearance().chapterId();
		this.firstAppearanceEpisodeId = fields.firstAppearance().episodeId();
		this.translations.clear();
		this.translations.putAll(fields.translations());
		PositionalRows.rewrite(this.epithets, fields.epithets(), CharacterEpithetEntity::new,
				CharacterEpithetEntity::rewrite);
		replace(this.aliases, fields.aliases());
		replace(this.species, fields.species());
		PositionalRows.rewrite(this.affiliations, fields.affiliations(), CharacterMembershipEntity::new,
				CharacterMembershipEntity::rewrite);
		replace(this.devilFruits, fields.devilFruits());
		replace(this.relatives, fields.relatives());
		PositionalRows.rewrite(this.gallery, fields.gallery(), CharacterImageEntity::new,
				CharacterImageEntity::rewrite);
		PositionalRows.rewrite(this.paragraphs, fields.paragraphs(), CharacterParagraphEntity::new,
				ParagraphEntity::rewrite);
		this.initialRomaji = fields.initialRomaji();
		this.initialJapaneseName = fields.initialJapaneseName();
		this.initialStatus = fields.initialStatus();
		this.initialTranslations.clear();
		this.initialTranslations.putAll(fields.initialTranslations());
		PositionalRows.rewrite(this.reveals, fields.reveals(), CharacterRevealEntity::new,
				CharacterRevealEntity::rewrite);
		touch(now);
	}

	private static <V> void replace(List<V> values, List<V> written) {
		values.clear();
		values.addAll(written);
	}

}
