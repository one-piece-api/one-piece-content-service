package dev.onepieceapi.contentservice.domain.workflow;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.Optional;

/** What the list of an entity section can be sorted by - the same for every entity. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ContentSortField implements SortField {

	/** The last update of the version shown - the default, newest first. */
	UPDATED_AT("updatedAt"),

	/**
	 * The natural key of the entity, by the name the Devil Fruit and its type give it.
	 */
	ROMAJI("romaji"),

	/**
	 * The natural key of an entity keyed by a number, e.g. a saga - by the name it gives
	 * it: the same order as {@link #ROMAJI}, which is the name of the key of the others.
	 */
	NUMBER("number"),

	/**
	 * The name in the caller's language, or else the natural key - what the row shows.
	 */
	NAME("name"),

	/** The username of the author of the version shown. */
	AUTHOR("author"),

	/**
	 * The status of the version shown, in lifecycle order (see {@code VersionStatus}).
	 */
	STATUS("status");

	private final String field;

	public static Optional<ContentSortField> of(String field) {
		return Arrays.stream(values()).filter(sortField -> sortField.field.equals(field)).findFirst();
	}

}
