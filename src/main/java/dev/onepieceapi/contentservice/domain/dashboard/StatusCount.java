package dev.onepieceapi.contentservice.domain.dashboard;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One tile of the dashboard (UF-CNT-19): how many contents have a version visible to the
 * caller in this status - contents, not versions, so it matches the list filtered by that
 * status.
 *
 * @param mine how many of them are the caller's, as {@link MineScope} defines it; null
 * for a status with no "mine"
 */
public record StatusCount(VersionStatus status, long contents, Long mine) {

	/**
	 * The statuses that get a tile, in workflow order. A superseded version is history,
	 * not work to do: it has no tile, as in the reference mockup.
	 */
	public static Set<VersionStatus> tracked() {
		return Arrays.stream(VersionStatus.values())
			.filter(status -> status != VersionStatus.SUPERSEDED)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(VersionStatus.class)));
	}

}
