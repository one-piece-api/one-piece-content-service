package dev.onepieceapi.contentservice.domain.workflow;

import dev.onepieceapi.contentservice.domain.security.Permission;

import java.util.Set;

import static dev.onepieceapi.contentservice.domain.workflow.TransitionDecision.ALLOWED;
import static dev.onepieceapi.contentservice.domain.workflow.TransitionDecision.CONFLICT;
import static dev.onepieceapi.contentservice.domain.workflow.TransitionDecision.FORBIDDEN;

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

	/**
	 * Who is asking comes first: a caller the action is not for is told so, whatever
	 * state the version is in.
	 */
	TransitionDecision decide(TransitionContext context) {
		if (!context.callerHolds(this.permission) || isRefused(context, FORBIDDEN)) {
			return FORBIDDEN;
		}
		if (!this.from.contains(context.status()) || isRefused(context, CONFLICT)) {
			return CONFLICT;
		}
		return ALLOWED;
	}

	boolean allows(TransitionContext context) {
		return decide(context) == ALLOWED;
	}

	private boolean isRefused(TransitionContext context, TransitionDecision refusal) {
		return this.conditions.stream()
			.filter(condition -> condition.refusesWith(refusal))
			.anyMatch(condition -> !context.satisfies(condition));
	}

}
