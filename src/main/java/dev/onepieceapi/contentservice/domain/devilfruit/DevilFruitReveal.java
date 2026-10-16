package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One reveal of a Devil Fruit (docs/user-flows/content-editorial-workflow.md 3.6;
 * implementation plan of the timeline, T9): the chapter it happens in and only the fields
 * revealed there, each with the value believed before - a field left null is not part of
 * it. The image never is.
 * <p>
 * The type and the subcategory are believed together (ADR-0010): a reveal defining the
 * type defines the subcategory as well, none when it names none; a reveal defining only
 * the subcategory keeps the type in force at it.
 *
 * @param revealedAt where the reader learns better; {@link StoryPoint#NONE} while not
 * chosen
 * @param typeContentId the content of the Devil Fruit Type believed; null when not part
 * of it
 * @param subcategoryId the subcategory believed; null for none
 * @param translations what was believed per language code; a field null is not part of it
 */
public record DevilFruitReveal(StoryPoint revealedAt, String romaji, UUID typeContentId, UUID subcategoryId,
		Map<String, DevilFruitTranslation> translations) implements Reveal {

	/** Nothing chosen is {@link StoryPoint#NONE}, no translation an empty map. */
	public DevilFruitReveal {
		revealedAt = StoryPoint.orNone(revealedAt);
		translations = translations == null ? Map.of() : translations;
	}

	DevilFruitReveal normalized() {
		Map<String, DevilFruitTranslation> written = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			DevilFruitTranslation normalized = translation.normalized();
			if (!normalized.isEmpty()) {
				written.put(language, normalized);
			}
		});
		return new DevilFruitReveal(this.revealedAt, Text.stripToNull(this.romaji), this.typeContentId,
				this.subcategoryId, written);
	}

	@Override
	public boolean definesNothing() {
		return this.romaji == null && !definesClassification()
				&& this.translations.values().stream().allMatch(DevilFruitTranslation::isEmpty);
	}

	/** Whether it defines the type, and so the subcategory with it. */
	public boolean definesType() {
		return this.typeContentId != null;
	}

	/** Whether it defines the type or the subcategory. */
	public boolean definesClassification() {
		return this.typeContentId != null || this.subcategoryId != null;
	}

	/**
	 * What it says in this language - nothing at all, when no translation was written.
	 */
	public DevilFruitTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, DevilFruitTranslation.NONE);
	}

	Set<String> languages() {
		return this.translations.keySet();
	}

}
