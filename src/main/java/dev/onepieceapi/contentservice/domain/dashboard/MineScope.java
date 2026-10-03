package dev.onepieceapi.contentservice.domain.dashboard;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;

import java.util.Optional;
import java.util.Set;

/**
 * What "mine" means for a status on the dashboard (UF-CNT-19): drafts can be narrowed to
 * the caller's own, reviews to the ones the caller holds. No other status has a "mine",
 * and a reviewer's "mine" only exists for whoever may claim.
 */
public enum MineScope {

	/** The versions the caller wrote. */
	AUTHORED,

	/** The versions the caller holds in review. */
	CLAIMED;

	public static Optional<MineScope> of(VersionStatus status, Set<Permission> permissions) {
		return switch (status) {
			case DRAFT -> permissions.contains(Permission.CONTENT_WRITE) ? Optional.of(AUTHORED) : Optional.empty();
			case IN_REVIEW -> permissions.contains(Permission.CONTENT_REVIEW) ? Optional.of(CLAIMED) : Optional.empty();
			default -> Optional.empty();
		};
	}

}
