package dev.onepieceapi.contentservice.domain.workflow;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What one version of a content says, whatever its entity: what the generic workflow
 * needs to know of it (docs/user-flows/content-editorial-workflow.md 3). Every entity has
 * a romaji and a name per language; each one adds its own fields, e.g.
 * {@code DevilFruitType}.
 *
 * @param <T> the entity's own record, so that {@link #normalized()} gives one back
 */
public interface ContentBody<T extends ContentBody<T>> {

	/** One value shared by every language - also what the public address comes from. */
	String romaji();

	/** The language codes something was written in. */
	Set<String> languages();

	/** The name per language code, for the languages that have one. */
	Map<String, String> names();

	/**
	 * The same content as it is stored and compared: no space around a text, a blank text
	 * counted as missing, nothing left for a language nothing was written in.
	 */
	T normalized();

	/**
	 * The same content with an id for each part that needs one and has none yet - e.g. a
	 * subcategory just added to a Devil Fruit Type; given by the service once the draft
	 * is valid, never by the client. Nothing to do by default.
	 */
	@SuppressWarnings("unchecked")
	default T identified() {
		return (T) this;
	}

	/**
	 * Every field still missing for review in the given languages (3.3, completeness),
	 * named as a request names it - e.g. {@code romaji}, {@code translations[it].name};
	 * empty when the content is complete.
	 */
	List<String> missingFields(Collection<String> languages);

}
