package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.readertime.FirstAppearing;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Optional;

/**
 * The rules of the first appearance (docs/user-flows/content-editorial-workflow.md 3.6,
 * 3.4.3; implementation plan of the timeline, T7; ADR-0008), written once for whatever is
 * {@link FirstAppearing}: its chapter is a relation like any other ({@link #relation},
 * followed through {@link RelationRules}), and an item that requires one cannot go online
 * - published or restored - without it. Not even {@code content:admin} lifts them.
 */
@UtilityClass
public class FirstAppearanceRules {

	/** The relation as a block names it: {@code firstAppearanceChapterId}. */
	private static final String NAME = "firstAppearanceChapter";

	private static final String NOT_LINKABLE = "must be a Chapter with an approved version";

	/**
	 * The chapter of a first appearance as a relation to the chapters.
	 * @param itemsName what the items pointing to a chapter are called when they keep it
	 * from being retired, e.g. {@code fruits}
	 */
	public <T extends FirstAppearing> RelationDescriptor<T> relation(String itemsName) {
		return RelationDescriptor.<T>builder()
			.name(NAME)
			.field(FirstAppearing.FIELD)
			.target(FirstAppearing::firstAppearanceChapterId)
			.notLinkable(NOT_LINKABLE)
			.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
			.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
			.itemsName(itemsName)
			.build();
	}

	/** Refuses an item going online without a first appearance, when it requires one. */
	public Optional<ActionBlock> missing(VersionAction action, FirstAppearing item) {
		if (!RelationRules.GOING_ONLINE.contains(action) || !item.lacksFirstAppearance()) {
			return Optional.empty();
		}
		return Optional.of(new ActionBlock(BlockReason.FIRST_APPEARANCE_MISSING, Map.of()));
	}

}
