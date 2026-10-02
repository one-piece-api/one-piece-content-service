package dev.onepieceapi.contentservice.domain.workflow;

import dev.onepieceapi.contentservice.domain.security.Permission;

import java.util.Set;

/**
 * One row of the transition table: an action, the statuses it starts from, the permission
 * it takes and what else must hold.
 */
record TransitionRule(VersionAction action, Set<VersionStatus> from, Permission permission,
		Set<TransitionCondition> conditions) {

	/** A rule asking for nothing beyond its permission. */
	TransitionRule(VersionAction action, Set<VersionStatus> from, Permission permission) {
		this(action, from, permission, Set.of());
	}

	boolean allows(TransitionContext context) {
		return this.from.contains(context.status()) && context.callerHolds(this.permission)
				&& this.conditions.stream().allMatch(context::satisfies);
	}

}
