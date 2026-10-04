package dev.onepieceapi.contentservice.domain.workflow;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Where a version stands in the editorial lifecycle
 * (docs/user-flows/content-editorial-workflow.md 4.2). Declared in lifecycle order, which
 * is also the order a list sorted by status follows.
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
	 * Still moving through the workflow. A content has at most one open version; every
	 * other status is closed, its content immutable (4.1).
	 */
	public boolean isOpen() {
		return this == DRAFT || this == IN_REVIEW || this == REJECTED || this == READY_TO_PUBLISH;
	}

	/** The statuses of a version still moving through the workflow. */
	public static Set<VersionStatus> open() {
		return Arrays.stream(values())
			.filter(VersionStatus::isOpen)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(VersionStatus.class)));
	}

	/** The statuses of a version that has left the workflow. */
	public static Set<VersionStatus> closed() {
		return EnumSet.complementOf(EnumSet.copyOf(open()));
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
