package dev.onepieceapi.contentservice.domain.workflow;

/**
 * Why an entity's own rules refuse an action the transition rules would allow
 * (implementation plan of the Devil Fruit, D3): a closed set, so a client can translate
 * each one. What each says is in {@link ActionBlock#detail()}.
 */
public enum BlockReason {

	/** The Devil Fruit Type of a fruit is not online, so the fruit cannot be. */
	TYPE_NOT_ONLINE,

	/** Devil Fruits online still point to the Devil Fruit Type. */
	ONLINE_FRUITS_LINKED,

	/**
	 * The subcategory of a fruit is not in the online version of its type, so the fruit
	 * cannot be online (implementation plan of the subcategories, S6).
	 */
	SUBCATEGORY_NOT_ONLINE,

	/**
	 * The version of a Devil Fruit Type leaves out subcategories that online fruits use
	 * (implementation plan of the subcategories, S7).
	 */
	SUBCATEGORY_IN_USE,

	/**
	 * The content a relation points to is not online, so the item cannot be
	 * (implementation plan of the story structure, S5): the generic code of every
	 * relation but the Devil Fruit's type; the detail names the relation, e.g.
	 * {@code sagaId}.
	 */
	RELATED_NOT_ONLINE,

	/**
	 * Items online still point to the content through a relation (S5); the detail names
	 * them, e.g. {@code arcs}.
	 */
	ONLINE_ITEMS_LINKED,

	/**
	 * The item has no first appearance, which it needs to be online (implementation plan
	 * of the timeline, T7): the spoiler filter could not place it. No detail.
	 */
	FIRST_APPEARANCE_MISSING,

	/**
	 * Another bounty online already replaces the same previous one (implementation plan
	 * of the Bounty, B2): a bounty is replaced by at most one. The detail names the
	 * previous ({@code previousId}) and the bounty replacing it ({@code replacedBy}, with
	 * its id and key).
	 */
	PREVIOUS_ALREADY_REPLACED,

	/**
	 * Going online, the bounty would close a cycle of previous bounties through the
	 * online ones (implementation plan of the Bounty, B2). The detail names the previous
	 * ({@code previousId}).
	 */
	BOUNTY_CHAIN_CYCLE,

	/**
	 * A membership holds a Marine rank in an affiliation whose online version is not a
	 * Navy one, so the character cannot be online (implementation plan of the Character,
	 * C8). The detail names the membership ({@code affiliationsIndex}) and the
	 * affiliation ({@code affiliationId}, {@code affiliationRomaji}).
	 */
	MARINE_RANK_NOT_ALLOWED,

	/**
	 * The version of an affiliation is not a Navy one while online characters hold a rank
	 * in it (implementation plan of the Character, C8). The detail has their
	 * {@code count} and the first {@code characters}, with id and romaji.
	 */
	MARINE_RANK_IN_USE

}
