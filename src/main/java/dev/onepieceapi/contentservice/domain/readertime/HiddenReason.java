package dev.onepieceapi.contentservice.domain.readertime;

/**
 * Why the preview at N hides an entry the editor may expect to see
 * (docs/user-flows/content-editorial-workflow.md 3.7.4, UF-CNT-23; reveals plan R6, D2,
 * D5). An entry simply discovered after N is not flagged: that is the point of the
 * filter.
 */
public enum HiddenReason {

	/** What it points to is online but met by the reader only after N. */
	TARGET_NOT_VISIBLE,

	/** What it points to has no version online: the reader never sees it. */
	TARGET_NOT_ONLINE,

	/** Its {@code discovered} point has no part on the axis read: not seen there. */
	NOT_ON_AXIS

}
