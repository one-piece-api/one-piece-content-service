package dev.onepieceapi.contentservice.domain.readertime;

import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * One reveal of an item (docs/user-flows/content-editorial-workflow.md 3.7.1): the point
 * where the reader learns, the fields learned there, a short title per language and, for
 * each field named, either the value learned or - when it holds none - the current value,
 * which stays in the main fields only. Not an event: the world does not change, only the
 * reader's knowledge.
 *
 * @param revealedAt where the reader learns; {@link StoryPoint#NONE} while not chosen
 * @param fields the fields learned there
 * @param values the values learned, for the fields not learned as the current value
 * @param titles the short title of the reveal per language code
 * @param <F> the fields of the entity a reveal may name
 * @param <V> the values of the entity
 */
public record Reveal<F extends Enum<F> & RevealableField, V extends RevealedValues<F, V>>(StoryPoint revealedAt,
		Set<F> fields, V values, Map<String, String> titles) {

	/** The longest title of a reveal. */
	public static final int TITLE_MAX_LENGTH = 60;

	/**
	 * Nothing chosen is {@link StoryPoint#NONE}, no field an empty set, no title an empty
	 * map.
	 */
	public Reveal {
		revealedAt = StoryPoint.orNone(revealedAt);
		fields = (fields == null || fields.isEmpty()) ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(fields));
		titles = (titles == null) ? Map.of() : Map.copyOf(titles);
	}

	/** The same reveal as it is stored and compared. */
	public Reveal<F, V> normalized() {
		return new Reveal<>(this.revealedAt, this.fields, this.values.normalized(), Text.written(this.titles));
	}

	/** Whether the reader learns this field here. */
	public boolean names(F field) {
		return this.fields.contains(field);
	}

	/** The title in this language; null when none was written. */
	public String titleIn(String language) {
		return this.titles.get(language);
	}

	/** Every language its title or a value is written in. */
	public Set<String> languages() {
		Set<String> languages = new TreeSet<>(this.values.languages());
		languages.addAll(this.titles.keySet());
		return languages;
	}

}
