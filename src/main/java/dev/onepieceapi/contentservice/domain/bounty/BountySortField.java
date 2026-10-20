package dev.onepieceapi.contentservice.domain.bounty;

import dev.onepieceapi.contentservice.domain.workflow.SortField;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What the list of bounties can be sorted by: the sorts every entity section has that
 * make sense for a bounty (the name being its key text), and the amount, which no other
 * entity has - and so no other list accepts it.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum BountySortField implements SortField {

	/** The last update of the version shown - the default, newest first. */
	UPDATED_AT("updatedAt"),

	/** The key text: the poster's name, then the amount. */
	NAME("name"),

	/** The Berry. */
	AMOUNT("amount"),

	/** The username of the author of the version shown. */
	AUTHOR("author"),

	/** The status of the version shown, in lifecycle order. */
	STATUS("status");

	private final String field;

}
