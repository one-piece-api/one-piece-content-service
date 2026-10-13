package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.service.content.RelationDescriptor;

/**
 * The relations between story entities (implementation plan of the story structure, S4,
 * S5), each with the generic reason codes: the detail names the relation ({@code sagaId},
 * {@code sagaNumber}) and the items pointing to it ({@code arcs}).
 */
public final class StoryRelations {

	/** The saga of an arc, required. */
	public static final RelationDescriptor<Arc> ARC_SAGA = RelationDescriptor.<Arc>builder()
		.name("saga")
		.target(Arc::sagaContentId)
		.notLinkable("must be a Saga with an approved version")
		.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
		.itemsName("arcs")
		.build();

	private StoryRelations() {
	}

}
