package dev.onepieceapi.contentservice.domain.workflow;

import dev.onepieceapi.contentservice.domain.security.Permission;

import java.util.Set;

import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_ADMIN;
import static dev.onepieceapi.contentservice.domain.workflow.TransitionDecision.ALLOWED;
import static dev.onepieceapi.contentservice.domain.workflow.TransitionDecision.CONFLICT;
import static dev.onepieceapi.contentservice.domain.workflow.TransitionDecision.FORBIDDEN;

/**
 * One row of the transition table: an action, the statuses it starts from, the permission
 * it takes and what else must hold.
 *
 * @param liftedByAdmin whether {@code content:admin} lifts the conditions on who the
 * caller is (2.3); the permission and the conditions on the state always hold
 */
record TransitionRule(VersionAction action, Set<VersionStatus> from, Permission permission,
		Set<TransitionCondition> conditions, boolean liftedByAdmin) {

	/** A rule asking for nothing beyond its permission. */
	TransitionRule(VersionAction action, Set<VersionStatus> from, Permission permission) {
		this(action, from, permission, Set.of());
	}

	/** A rule whose conditions hold for everyone. */
	TransitionRule(VersionAction action, Set<VersionStatus> from, Permission permission,
			Set<TransitionCondition> conditions) {
		this(action, from, permission, conditions, false);
	}

	/** The same rule, its conditions on the caller lifted for {@code content:admin}. */
	TransitionRule overridable() {
		return new TransitionRule(this.action, this.from, this.permission, this.conditions, true);
	}

	/**
	 * Who is asking comes first: a caller the action is not for is told so, whatever
	 * state the version is in.
	 */
	TransitionDecision decide(TransitionContext context) {
		boolean forCaller = isForCaller(context) || isOverriddenBy(context);
		if (!context.callerHolds(this.permission) || !forCaller) {
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

	/**
	 * Whether the caller may perform the action only through {@code content:admin}: it is
	 * allowed, but someone else's to perform.
	 */
	boolean overrides(TransitionContext context) {
		return allows(context) && !isForCaller(context);
	}

	private boolean isForCaller(TransitionContext context) {
		return !isRefused(context, FORBIDDEN);
	}

	private boolean isOverriddenBy(TransitionContext context) {
		return this.liftedByAdmin && context.callerHolds(CONTENT_ADMIN);
	}

	private boolean isRefused(TransitionContext context, TransitionDecision refusal) {
		return this.conditions.stream()
			.filter(condition -> condition.refusesWith(refusal))
			.anyMatch(condition -> !context.satisfies(condition));
	}

}
