package dev.onepieceapi.contentservice.domain.event;

import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.SortField;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What the list of events can be sorted by: the fields of every entity section that an
 * event has - not the natural key, it has none - and the order of the world
 * (implementation plan of the timeline, TL4), its default.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum EventSortField implements SortField {

	/**
	 * When it occurs, in the order of the world (see {@link EventTimeOrder}): every world
	 * date by its first year, then every chapter by its number - the default.
	 */
	OCCURS_AT("occursAt"),

	UPDATED_AT(ContentSortField.UPDATED_AT.field()),

	NAME(ContentSortField.NAME.field()),

	AUTHOR(ContentSortField.AUTHOR.field()),

	STATUS(ContentSortField.STATUS.field());

	private final String field;

}
