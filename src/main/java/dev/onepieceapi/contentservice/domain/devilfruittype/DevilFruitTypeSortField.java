package dev.onepieceapi.contentservice.domain.devilfruittype;

import dev.onepieceapi.contentservice.domain.workflow.SortField;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.Optional;

/** What the list of Devil Fruit Types can be sorted by. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum DevilFruitTypeSortField implements SortField {

	/** The last update of the version shown - the default, newest first. */
	UPDATED_AT("updatedAt"),

	ROMAJI("romaji");

	private final String field;

	public static Optional<DevilFruitTypeSortField> of(String field) {
		return Arrays.stream(values()).filter(sortField -> sortField.field.equals(field)).findFirst();
	}

}
