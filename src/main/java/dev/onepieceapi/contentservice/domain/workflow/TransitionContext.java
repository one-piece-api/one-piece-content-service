package dev.onepieceapi.contentservice.domain.workflow;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;

import java.util.Set;

/**
 * What the transition rules are checked against: a version, the one thing they need to
 * know about the rest of its content, and who is asking.
 *
 * @param contentHasOpenVersion whether any version of the content is open - visible to
 * the caller or not
 * @param permissions the ones the caller holds
 */
public record TransitionContext(Version<?> version, boolean contentHasOpenVersion, User caller,
		Set<Permission> permissions) {

	/**
	 * The same question asked again once a transition has moved the version. A version
	 * leaving the open statuses was its content's only open one (4.1), so the content has
	 * none left; any other move leaves that unchanged.
	 */
	public TransitionContext after(Version<?> moved) {
		boolean closedNow = status().isOpen() && !moved.status().isOpen();
		return new TransitionContext(moved, this.contentHasOpenVersion && !closedNow, this.caller, this.permissions);
	}

	VersionStatus status() {
		return this.version.status();
	}

	boolean callerHolds(Permission permission) {
		return this.permissions.contains(permission);
	}

	boolean callerIsAuthor() {
		return this.version.isAuthoredBy(this.caller);
	}

	boolean callerIsClaimant() {
		return this.version.isClaimedBy(this.caller);
	}

	boolean isClaimed() {
		return this.version.isClaimed();
	}

	boolean satisfies(TransitionCondition condition) {
		return condition.isMetBy(this);
	}

}
