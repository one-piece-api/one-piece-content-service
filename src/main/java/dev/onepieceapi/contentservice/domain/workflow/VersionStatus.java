package dev.onepieceapi.contentservice.domain.workflow;

/**
 * Where a version stands in the editorial lifecycle
 * (docs/user-flows/content-editorial-workflow.md 4.2).
 */
public enum VersionStatus {

	/** Being written by its author. */
	DRAFT,

	/** Submitted; waiting for, or held by, a reviewer. */
	IN_REVIEW,

	/** Review failed, with a mandatory reason. Frozen until returned to draft. */
	REJECTED,

	/** Review passed. */
	READY_TO_PUBLISH,

	/** Online. */
	PUBLISHED,

	/** Approved but set aside without ever going online. */
	ARCHIVED,

	/** Was online, explicitly taken down. */
	RETIRED,

	/** Was online, replaced by another version taking its place. */
	SUPERSEDED;

	/**
	 * Still moving through the workflow. An item has at most one open version; every
	 * other status is closed, its content immutable (4.1).
	 */
	public boolean isOpen() {
		return this == DRAFT || this == IN_REVIEW || this == REJECTED || this == READY_TO_PUBLISH;
	}

	/**
	 * Whether a version in this status has been online at some point. Derived, not
	 * stored: {@code RETIRED} and {@code SUPERSEDED} are only ever reached from
	 * {@code PUBLISHED}, and no other status is.
	 */
	public boolean everPublished() {
		return this == PUBLISHED || this == RETIRED || this == SUPERSEDED;
	}

}
