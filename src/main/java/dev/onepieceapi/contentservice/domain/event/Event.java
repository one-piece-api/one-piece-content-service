package dev.onepieceapi.contentservice.domain.event;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.story.StoryTranslations;
import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What one version of an event says (docs/user-flows/content-editorial-workflow.md 3.1.8;
 * implementation plan of the timeline, T2-T5): when it happens in the world and, for a
 * long one, when it ends; where the reader learns of it; the Devil Fruits taking part in
 * it; a title and an optional description per language. It has no natural key: an event
 * is addressed by its id, and two may share a title.
 *
 * @param occursAt when it happens, required for review; null while not written
 * @param endsAt when it ends, for a long event (a war, the Reverie); null for none
 * @param revealedAt the chapter where the reader learns of it, when it is not the one it
 * occurs in; {@link StoryPoint#NONE} for none
 * @param devilFruitContentIds the contents of the fruits taking part in it, in the order
 * of their ids, each once; none is complete
 * @param translations title and description per language code
 */
@Builder(toBuilder = true)
public record Event(EventTime occursAt, EventTime endsAt, StoryPoint revealedAt, List<UUID> devilFruitContentIds,
		Map<String, StoryTranslation> translations) implements ContentBody<Event> {

	public static final String OCCURS_AT_FIELD = "occursAt";

	public static final String ENDS_AT_FIELD = "endsAt";

	public static final String REVEALED_AT_FIELD = "revealedAt.chapter";

	/** Nothing written is empty or {@link StoryPoint#NONE}, not null. */
	public Event {
		revealedAt = StoryPoint.orNone(revealedAt);
		devilFruitContentIds = devilFruitContentIds == null ? List.of() : devilFruitContentIds;
		translations = translations == null ? Map.of() : translations;
	}

	/** Each participant once, in the order of their ids: two lists alike say the same. */
	@Override
	public Event normalized() {
		return toBuilder().devilFruitContentIds(this.devilFruitContentIds.stream().distinct().sorted().toList())
			.translations(StoryTranslations.normalized(this.translations))
			.build();
	}

	@Override
	public String nameField() {
		return StoryTranslations.TITLE;
	}

	/** Two events may share a title: the Reverie happens every four years. */
	@Override
	public boolean uniqueNames() {
		return false;
	}

	@Override
	public Set<String> languages() {
		return this.translations.keySet();
	}

	/** The titles: what an event is called. */
	@Override
	public Map<String, String> names() {
		return StoryTranslations.titles(this.translations);
	}

	/**
	 * When it happens, then a title per language - every one missing; never its end,
	 * where the reader learns of it, its participants or a description.
	 */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.occursAt == null) {
			missing.add(OCCURS_AT_FIELD);
		}
		StoryTranslations.addMissingTitles(this.translations, languages, missing);
		return missing;
	}

	/** The chapter it occurs in; null for a world date, or none. */
	public UUID occursAtChapterId() {
		return chapterOf(this.occursAt);
	}

	/** The chapter it ends in; null for a world date, or none. */
	public UUID endsAtChapterId() {
		return chapterOf(this.endsAt);
	}

	/** The chapter written as where the reader learns of it; null for none. */
	public UUID revealedAtChapterId() {
		return this.revealedAt.chapterContentId();
	}

	/**
	 * Where the reader learns of it (T5): the chapter written, or else the chapter it
	 * occurs in; empty when neither is - an event before the story with no reveal written
	 * cannot be placed on the reader axis. Computed, never stored.
	 */
	public Optional<UUID> effectiveRevealedAtChapterId() {
		return Optional.ofNullable(revealedAtChapterId()).or(() -> Optional.ofNullable(occursAtChapterId()));
	}

	private static UUID chapterOf(EventTime time) {
		return time == null ? null : time.chapterContentId();
	}

}
