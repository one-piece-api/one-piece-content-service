package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * What one version of an event says (implementation plan of the timeline, T2-T5, TL2):
 * when it occurs and ends - each a chapter or a world date, at most one of the two - the
 * chapter where the reader learns of it, the contents of the Devil Fruits taking part in
 * it and, per language, its title and description. No natural key. The entity types of
 * the links are constants the database fills in: they keep an event from pointing to
 * anything but chapters and Devil Fruits.
 */
@Entity
@Table(name = "event_version")
@Getter
@NoArgsConstructor
@FieldNameConstants
public class EventVersionEntity extends VersionBodyEntity {

	/** The chapter it occurs in; null for a world date, or none. */
	@Column(name = "occurs_chapter_id")
	private UUID occursAtChapterId;

	/** The world date it occurs at; null for a chapter, or none. */
	@Embedded
	@AttributeOverride(name = "yearFrom", column = @Column(name = "occurs_year_from"))
	@AttributeOverride(name = "yearTo", column = @Column(name = "occurs_year_to"))
	@AttributeOverride(name = "approximate", column = @Column(name = "occurs_approximate"))
	private WorldDateEmbeddable occursAtWorldDate;

	/** The chapter it ends in; null for a world date, or none. */
	@Column(name = "ends_chapter_id")
	private UUID endsAtChapterId;

	/** The world date it ends at; null for a chapter, or none. */
	@Embedded
	@AttributeOverride(name = "yearFrom", column = @Column(name = "ends_year_from"))
	@AttributeOverride(name = "yearTo", column = @Column(name = "ends_year_to"))
	@AttributeOverride(name = "approximate", column = @Column(name = "ends_approximate"))
	private WorldDateEmbeddable endsAtWorldDate;

	/** The chapter written as where the reader learns of it; null for none. */
	private UUID revealedAtChapterId;

	/** The contents of the fruits taking part in it - the relation {@code devilFruit}. */
	@ElementCollection
	@CollectionTable(name = "event_version_devil_fruit", joinColumns = @JoinColumn(name = "version_id"))
	@Column(name = "devil_fruit_content_id")
	private Set<UUID> devilFruitContentIds = new HashSet<>();

	/**
	 * The localized fields per language code; a language nothing was saved for is absent.
	 */
	@ElementCollection
	@CollectionTable(name = "event_version_translation", joinColumns = @JoinColumn(name = "version_id"))
	@MapKeyColumn(name = "language_code")
	private Map<String, StoryTranslationEmbeddable> translations = new HashMap<>();

	public EventVersionEntity(ContentVersionEntity version) {
		super(version);
	}

	/** The titles: an event has no natural key to be called by. */
	@Override
	public Map<String, String> names() {
		Map<String, String> titles = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			if (translation.getName() != null) {
				titles.put(language, translation.getName());
			}
		});
		return titles;
	}

	/** Replaces everything the version says; the caller notes when ({@link #touch}). */
	public void rewrite(UUID newOccursAtChapterId, WorldDateEmbeddable newOccursAtWorldDate, UUID newEndsAtChapterId,
			WorldDateEmbeddable newEndsAtWorldDate, UUID newRevealedAtChapterId,
			Collection<UUID> newDevilFruitContentIds, Map<String, StoryTranslationEmbeddable> newTranslations) {
		this.occursAtChapterId = newOccursAtChapterId;
		this.occursAtWorldDate = newOccursAtWorldDate;
		this.endsAtChapterId = newEndsAtChapterId;
		this.endsAtWorldDate = newEndsAtWorldDate;
		this.revealedAtChapterId = newRevealedAtChapterId;
		this.devilFruitContentIds.clear();
		this.devilFruitContentIds.addAll(newDevilFruitContentIds);
		this.translations.clear();
		this.translations.putAll(newTranslations);
	}

}
