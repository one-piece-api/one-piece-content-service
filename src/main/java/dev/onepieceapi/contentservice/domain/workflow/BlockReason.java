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
	 * The event has nowhere the reader learns of it - no revealed-at chapter, and it does
	 * not occur in a chapter - which it needs to be online (implementation plan of the
	 * timeline, T5): the spoiler filter could not place it. No detail.
	 */
	REVEAL_MISSING,

	/**
	 * The event is revealed before Devil Fruits taking part in it first appear, so it
	 * cannot be online (T5): the reader would learn of a fruit through it. The detail
	 * names them, {@code fruits}, with how many there are.
	 */
	REVEALED_BEFORE_PARTICIPANT,

	/**
	 * Online events the Devil Fruit takes part in are revealed before its new first
	 * appearance, so this version cannot be online (T5) - the same rule seen from the
	 * fruit. The detail names them, {@code events}, with how many there are.
	 */
	EVENTS_REVEALED_BEFORE,

	/**
	 * The knowledge phases are not in the order of the story as the chapters online
	 * number it - each after the first appearance and after the one before
	 * (implementation plan of the timeline, T9; ADR-0010) - e.g. a chapter was renumbered
	 * since the save. The detail names the first phase out of order, {@code phase}, by
	 * its position.
	 */
	PHASES_OUT_OF_ORDER

}
