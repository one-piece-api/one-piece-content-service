package dev.onepieceapi.contentservice.domain.readertime;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Values the reader holds of an item that are not its current ones
 * (docs/user-flows/content-editorial-workflow.md 3.7.1): what is known at the first
 * appearance, or what a reveal teaches. Only some fields are held; one not held is not
 * part of it. One record per entity.
 *
 * @param <F> the fields of the entity a reveal may name
 * @param <V> the record itself
 */
public interface RevealedValues<F extends Enum<F> & RevealableField, V extends RevealedValues<F, V>> {

	/**
	 * Whether it holds some value of the field: in some language, for a translated one.
	 */
	boolean holds(F field);

	/**
	 * Whether it holds the value the field cannot do without
	 * ({@link RevealableField#keyField()}).
	 */
	boolean holdsKey(F field);

	/**
	 * Where a request names each value of the field it holds, relative to the values,
	 * e.g. {@code romaji} or {@code translations[en].name}.
	 */
	List<String> valueFields(F field);

	/**
	 * Each translated value of the field missing in one of these languages, named as
	 * {@link #valueFields}: what submitting asks of a field it holds.
	 */
	List<String> missingTranslations(F field, Collection<String> languages);

	/** Every language a value is written in. */
	Set<String> languages();

	/** The same values as they are stored and compared: no space around a text. */
	V normalized();

	/** The romaji it holds, which gives a slug (3.3); null for none. */
	default String romaji() {
		return null;
	}

}
