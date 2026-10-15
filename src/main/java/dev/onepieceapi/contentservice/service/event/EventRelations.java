package dev.onepieceapi.contentservice.service.event;

import dev.onepieceapi.contentservice.domain.event.Event;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.service.content.RelationDescriptor;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * The relations of an event (implementation plan of the timeline, T3, T5), each with the
 * generic reason codes: the Devil Fruits taking part in it - a relation to many, a
 * violation naming the one at fault ({@code participants.devilFruits[<id>]}) - and the
 * chapters it occurs in, ends in and is revealed in. The chapters keep it from being
 * retired alike: one block names the events, whichever of the three they use.
 */
public final class EventRelations {

	/** The Devil Fruits taking part, none or many; the list is filtered by it. */
	public static final RelationDescriptor<Event> DEVIL_FRUITS = RelationDescriptor.<Event>builder()
		.name("devilFruit")
		.field("participants.devilFruits")
		.targets(Event::devilFruitContentIds)
		.notLinkable("must be a Devil Fruit with an approved version")
		.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
		.itemsName(EventRelations.EVENTS)
		.build();

	/** The chapter it occurs in, when it occurs in the story. */
	public static final RelationDescriptor<Event> OCCURS_AT_CHAPTER = chapter("occursAtChapter", "occursAt.chapter",
			Event::occursAtChapterId);

	/** The chapter it ends in, when a long event ends in the story. */
	public static final RelationDescriptor<Event> ENDS_AT_CHAPTER = chapter("endsAtChapter", "endsAt.chapter",
			Event::endsAtChapterId);

	/** The chapter written as where the reader learns of it. */
	public static final RelationDescriptor<Event> REVEALED_AT_CHAPTER = chapter("revealedAtChapter",
			Event.REVEALED_AT_FIELD, Event::revealedAtChapterId);

	/** The three relations to a chapter, in the order a request reads them. */
	public static final List<RelationDescriptor<Event>> CHAPTERS = List.of(OCCURS_AT_CHAPTER, ENDS_AT_CHAPTER,
			REVEALED_AT_CHAPTER);

	/** What the events keeping a fruit or a chapter from being retired are called. */
	private static final String EVENTS = "events";

	private static final String CHAPTER_NOT_LINKABLE = "must be a Chapter with an approved version";

	private EventRelations() {
	}

	private static RelationDescriptor<Event> chapter(String name, String field, Function<Event, UUID> target) {
		return RelationDescriptor.<Event>builder()
			.name(name)
			.field(field)
			.target(target)
			.notLinkable(CHAPTER_NOT_LINKABLE)
			.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
			.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
			.itemsName(EVENTS)
			.build();
	}

}
