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
	FIRST_APPEARANCE_MISSING

}
