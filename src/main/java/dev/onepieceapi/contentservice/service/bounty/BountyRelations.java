package dev.onepieceapi.contentservice.service.bounty;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.RelationDescriptor;

/**
 * The relations of a bounty (implementation plan of the Bounty, B2): the bounty it
 * replaces, its holder, and the chapter and the episode it first appears in (ADR-0008).
 */
public final class BountyRelations {

	/** The bounty a bounty replaces. */
	public static final RelationDescriptor<Bounty> PREVIOUS = RelationDescriptor.<Bounty>builder()
		.name("previous")
		.target(Bounty::previousContentId)
		.notLinkable("must be a bounty with an approved version")
		.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
		.itemsName("bounties")
		.build();

	/** The character a bounty is about. */
	public static final RelationDescriptor<Bounty> HOLDER = RelationDescriptor.<Bounty>builder()
		.name("holder")
		.target(Bounty::holderContentId)
		.notLinkable("must be a character with an approved version")
		.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
		.itemsName("bounties")
		.build();

	/** The chapter a bounty first appears in (ADR-0008). */
	public static final RelationDescriptor<Bounty> FIRST_APPEARANCE_CHAPTER = FirstAppearanceRules
		.chapterRelation("bounties");

	/** The episode a bounty first appears in (ADR-0008). */
	public static final RelationDescriptor<Bounty> FIRST_APPEARANCE_EPISODE = FirstAppearanceRules
		.episodeRelation("bounties");

	private BountyRelations() {
	}

}
