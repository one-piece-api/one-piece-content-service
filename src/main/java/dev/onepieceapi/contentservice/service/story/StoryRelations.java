package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.service.content.RelationDescriptor;

/**
 * The relations between story entities (implementation plan of the story structure, S4,
 * S5), each with the generic reason codes: the detail names the relation ({@code sagaId},
 * {@code sagaNumber}) and the items pointing to it ({@code arcs}). A relation that is
 * optional is no different here: a version that points to nothing needs and blocks
 * nothing, which {@code RelationRules} already does.
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

	/** The arc of a chapter, required. */
	public static final RelationDescriptor<Chapter> CHAPTER_ARC = RelationDescriptor.<Chapter>builder()
		.name("arc")
		.target(Chapter::arcContentId)
		.notLinkable("must be an Arc with an approved version")
		.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
		.itemsName("chapters")
		.build();

	/** The volume that collects a chapter, optional. */
	public static final RelationDescriptor<Chapter> CHAPTER_VOLUME = RelationDescriptor.<Chapter>builder()
		.name("volume")
		.target(Chapter::volumeContentId)
		.notLinkable("must be a Volume with an approved version")
		.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
		.itemsName("chapters")
		.build();

	/** The arc of an episode, optional: a filler belongs to none. */
	public static final RelationDescriptor<Episode> EPISODE_ARC = RelationDescriptor.<Episode>builder()
		.name("arc")
		.target(Episode::arcContentId)
		.notLinkable("must be an Arc with an approved version")
		.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
		.itemsName("episodes")
		.build();

	private StoryRelations() {
	}

}
