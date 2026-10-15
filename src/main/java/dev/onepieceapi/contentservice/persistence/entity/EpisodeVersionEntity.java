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
import java.util.Optional;
import java.util.UUID;

/**
 * What one version of an episode says (implementation plan of the story structure, S2,
 * S4, S13): its number (its natural key), the content of its arc if any, the Japanese
 * title, the broadcast day per country and, per language, its title and description. The
 * entity type of the link is a constant the database fills in: it keeps an episode from
 * pointing to anything but an arc.
 */
@Entity
@Table(name = "episode_version")
@Getter
@NoArgsConstructor
@FieldNameConstants
public class EpisodeVersionEntity extends VersionBodyEntity {

	/** The natural key of the entity; null while not written. */
	private Integer number;

	/** The arc's content, not one of its versions; null for none (a filler). */
	private UUID arcContentId;

	private String japaneseTitle;

	/** The broadcast day by country code. */
	@ElementCollection
	@CollectionTable(name = "episode_version_broadcast", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "country")
	@Column(name = "broadcast_date")
	private Map<String, LocalDate> broadcasts = new HashMap<>();

	/**
	 * The localized fields per language code; a language nothing was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "episode_version_translation", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, StoryTranslationEmbeddable> translations = new HashMap<>();

	public EpisodeVersionEntity(ContentVersionEntity version) {
		super(version);
	}

	@Override
	public Optional<NaturalKey> naturalKey() {
		return Optional.of(new NaturalKey(Fields.number, this.number));
	}

	/** Replaces everything the version says, and notes when. */
	public void rewrite(Integer newNumber, UUID newArcContentId, String newJapaneseTitle,
			Map<String, LocalDate> newBroadcasts, Map<String, StoryTranslationEmbeddable> newTranslations,
			Instant now) {
		this.number = newNumber;
		this.arcContentId = newArcContentId;
		this.japaneseTitle = newJapaneseTitle;
		this.broadcasts.clear();
		this.broadcasts.putAll(newBroadcasts);
		this.translations.clear();
		this.translations.putAll(newTranslations);
		touch(now);
	}

}
