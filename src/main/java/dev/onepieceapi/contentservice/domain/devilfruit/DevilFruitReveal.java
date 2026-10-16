package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One reveal of a Devil Fruit (docs/user-flows/content-editorial-workflow.md 3.6;
 * implementation plan of the timeline, T13): the chapter it happens in, the fields the
 * reader learns there and, for those not yet the truth, the value learned. The last
 * reveal of a field holds no value for it: from there on the field is the truth, which
 * stays in the fruit's main fields only.
 *
 * @param revealedAt where the reader learns; {@link StoryPoint#NONE} while not chosen
 * @param fields the fields learned there; the type stands for the subcategory too
 * @param values the values learned, for the fields not yet the truth
 * @param titles the short title of the reveal per language code (T10)
 */
public record DevilFruitReveal(StoryPoint revealedAt, Set<DevilFruitField> fields, DevilFruitBelief values,
		Map<String, String> titles) implements Reveal {

	/** The longest title of a reveal. */
	public static final int TITLE_MAX_LENGTH = 60;

	/**
	 * Nothing chosen is {@link StoryPoint#NONE}, no field an empty set, no value
	 * {@link DevilFruitBelief#NONE}, no title an empty map.
	 */
	public DevilFruitReveal {
		revealedAt = StoryPoint.orNone(revealedAt);
		fields = fields == null || fields.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(fields));
		values = values == null ? DevilFruitBelief.NONE : values;
		titles = titles == null ? Map.of() : titles;
	}

	DevilFruitReveal normalized() {
		Map<String, String> writtenTitles = new TreeMap<>();
		this.titles.forEach((language, title) -> {
			String stripped = Text.stripToNull(title);
			if (stripped != null) {
				writtenTitles.put(language, stripped);
			}
		});
		return new DevilFruitReveal(this.revealedAt, this.fields, this.values.normalized(), writtenTitles);
	}

	/** It names no field: a title alone is about the reveal, not a value. */
	@Override
	public boolean definesNothing() {
		return this.fields.isEmpty();
	}

	/** Whether the reader learns this field here. */
	public boolean reveals(DevilFruitField field) {
		return this.fields.contains(field);
	}

	/** The title of the reveal in this language; null when none was written. */
	public String titleIn(String language) {
		return this.titles.get(language);
	}

	public Set<String> languages() {
		Set<String> languages = new TreeSet<>(this.values.languages());
		languages.addAll(this.titles.keySet());
		return languages;
	}

}
