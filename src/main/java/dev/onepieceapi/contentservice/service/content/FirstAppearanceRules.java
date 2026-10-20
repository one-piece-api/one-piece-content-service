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
 * {@link FirstAppearing}: its chapter and its episode are relations like any other
 * ({@link #chapterRelation}, {@link #episodeRelation}, followed through
 * {@link RelationRules}), and an item that requires one cannot go online - published or
 * restored - without at least one of the two. Not even {@code content:admin} lifts them.
 */
@UtilityClass
public class FirstAppearanceRules {

	/** The relation as a block names it: {@code firstAppearanceChapterId}. */
	private static final String CHAPTER_NAME = "firstAppearanceChapter";

	private static final String CHAPTER_NOT_LINKABLE = "must be a Chapter with an approved version";

	/** The relation as a block names it: {@code firstAppearanceEpisodeId}. */
	private static final String EPISODE_NAME = "firstAppearanceEpisode";

	private static final String EPISODE_NOT_LINKABLE = "must be an Episode with an approved version";

	/**
	 * The chapter of a first appearance as a relation to the chapters.
	 * @param itemsName what the items pointing to a chapter are called when they keep it
	 * from being retired, e.g. {@code fruits}
	 */
	public <T extends FirstAppearing> RelationDescriptor<T> chapterRelation(String itemsName) {
		return RelationDescriptor.<T>builder()
			.name(CHAPTER_NAME)
			.field(FirstAppearing.CHAPTER_FIELD)
			.target(FirstAppearing::firstAppearanceChapterId)
			.notLinkable(CHAPTER_NOT_LINKABLE)
			.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
			.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
			.itemsName(itemsName)
			.build();
	}

	/**
	 * The episode of a first appearance as a relation to the episodes.
	 * @param itemsName as {@link #chapterRelation}
	 */
	public <T extends FirstAppearing> RelationDescriptor<T> episodeRelation(String itemsName) {
		return RelationDescriptor.<T>builder()
			.name(EPISODE_NAME)
			.field(FirstAppearing.EPISODE_FIELD)
			.target(FirstAppearing::firstAppearanceEpisodeId)
			.notLinkable(EPISODE_NOT_LINKABLE)
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
