package dev.onepieceapi.contentservice.domain.workflow;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What one version of a content says, whatever its entity: what the generic workflow
 * needs to know of it (docs/user-flows/content-editorial-workflow.md 3). Every entity has
 * a name per language, most of them a natural key too; each one adds its own fields, e.g.
 * {@code DevilFruitType}.
 *
 * @param <T> the entity's own record, so that {@link #normalized()} gives one back
 */
public interface ContentBody<T extends ContentBody<T>> {

	/** What most entities call the localized field they are named by. */
	String DEFAULT_NAME_FIELD = "name";

	/**
	 * One value shared by every language, unique within the entity - also what the public
	 * address comes from; empty for an entity that has none, whose contents are addressed
	 * by their id only, e.g. an event (implementation plan of the timeline, TL2). None by
	 * default.
	 */
	default Optional<NaturalKey> naturalKey() {
		return Optional.empty();
	}

	/**
	 * The natural key as text, e.g. to name the content; {@code null} while not written,
	 * or for an entity without one.
	 */
	default String naturalKeyText() {
		return naturalKey().map(NaturalKey::text).orElse(null);
	}

	/**
	 * The localized field a content is called by, named as a request names it inside a
	 * translation: {@code name} unless the entity says otherwise, e.g. {@code title}.
	 */
	default String nameField() {
		return DEFAULT_NAME_FIELD;
	}

	/**
	 * Whether no two contents of the entity may share a name in a language; true unless
	 * the entity says otherwise - a chapter or an episode may repeat a title, its number
	 * being what tells them apart (implementation plan of the story structure, section
	 * 7).
	 */
	default boolean uniqueNames() {
		return true;
	}

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
