package dev.onepieceapi.contentservice.domain.dashboard;

import dev.onepieceapi.contentservice.domain.workflow.SortField;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.Optional;

/**
 * What a dashboard status page can be sorted by: rows of different kinds share only their
 * workflow, so only its fields.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum DashboardSortField implements SortField {

	/** The last update of the version shown - the default, newest first. */
	UPDATED_AT("updatedAt");

	private final String field;

	public static Optional<DashboardSortField> of(String field) {
		return Arrays.stream(values()).filter(sortField -> sortField.field.equals(field)).findFirst();
	}

}
