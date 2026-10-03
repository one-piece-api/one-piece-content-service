package dev.onepieceapi.contentservice.domain.workflow;

import lombok.RequiredArgsConstructor;

import java.util.function.Predicate;

import static dev.onepieceapi.contentservice.domain.workflow.TransitionDecision.CONFLICT;
import static dev.onepieceapi.contentservice.domain.workflow.TransitionDecision.FORBIDDEN;

/**
 * What a transition may require beyond its permission - the "Requires" column of
 * docs/user-flows/content-editorial-workflow.md 4.2, one named condition each. A
 * condition is either about who is asking or about the state of things, which decides how
 * an action is refused when it is not met.
 */
@RequiredArgsConstructor
enum TransitionCondition {

	/** The caller wrote the version. */
	AUTHOR(TransitionContext::callerIsAuthor, FORBIDDEN),

	/** The caller did not write the version: nobody reviews their own (8.4). */
	NOT_AUTHOR(Predicate.not(TransitionContext::callerIsAuthor), FORBIDDEN),

	/** No reviewer holds the version. */
	UNCLAIMED(Predicate.not(TransitionContext::isClaimed), CONFLICT),

	/**
	 * A reviewer holds the version - implied by {@link #CLAIMANT}, and what is left of it
	 * once {@code content:admin} lifts that one: there must be a claim to release.
	 */
	CLAIMED(TransitionContext::isClaimed, CONFLICT),

	/**
	 * The caller is the reviewer holding the version: whoever decides holds the claim.
	 */
	CLAIMANT(TransitionContext::callerIsClaimant, FORBIDDEN),

	/** The content has no open version: there is room for one (4.1). */
	NO_OPEN_VERSION(Predicate.not(TransitionContext::contentHasOpenVersion), CONFLICT);

	private final Predicate<TransitionContext> check;

	private final TransitionDecision refusal;

	boolean isMetBy(TransitionContext context) {
		return this.check.test(context);
	}

	/** Whether this condition, when not met, refuses the action in the given way. */
	boolean refusesWith(TransitionDecision decision) {
		return this.refusal == decision;
	}

}
