package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What one version of a bounty says (implementation plan of the Bounty, D1-D5): the
 * poster's name and the amount, which together are its natural key, the condition, the
 * inactive flag, the content of the bounty it replaces, its image, its first appearance
 * (ADR-0008) and, per language, its description. The entity types of the links are
 * constants the database fills in: they keep a bounty from pointing to anything but a
 * bounty, a chapter and an episode.
 */
@Entity
@Table(name = "bounty_version")
@Getter
@NoArgsConstructor
@FieldNameConstants
public class BountyVersionEntity extends VersionBodyEntity {

	private String posterName;

	private Long amount;

	@Enumerated(EnumType.STRING)
	private BountyCondition condition;

	private boolean inactive;

	/**
	 * The key text, written by the database from the two fields it is made of: what the
	 * list searches and sorts by, and what must be unique. Never written from here.
	 */
	@Column(insertable = false, updatable = false)
	@Generated(event = { EventType.INSERT, EventType.UPDATE })
	private String naturalKey;

	/** The content of the bounty it replaces; null for none. */
	private UUID previousContentId;

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
	@CollectionTable(name = "bounty_version_translation", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, BountyTranslationEmbeddable> translations = new HashMap<>();

	public BountyVersionEntity(ContentVersionEntity version) {
		super(version);
	}

	/** Built from the two fields, as the domain does, not read from the column. */
	@Override
	public NaturalKey naturalKey() {
		return Bounty.naturalKeyOf(this.posterName, this.amount);
	}

	/** Replaces everything the version says, and notes when. */
	public void rewrite(BountyFields fields, Instant now) {
		this.posterName = fields.posterName();
		this.amount = fields.amount();
		this.condition = fields.condition();
		this.inactive = fields.inactive();
		this.previousContentId = fields.previousContentId();
		this.imageId = fields.imageId();
		this.firstAppearanceChapterId = fields.firstAppearance().chapterId();
		this.firstAppearanceEpisodeId = fields.firstAppearance().episodeId();
		this.translations.clear();
		this.translations.putAll(fields.translations());
		touch(now);
	}

}
