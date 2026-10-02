package dev.onepieceapi.contentservice.domain.workflow;

import lombok.RequiredArgsConstructor;

import java.util.function.Predicate;

/**
 * What a transition may require beyond its permission - the "Requires" column of
 * docs/user-flows/content-editorial-workflow.md 4.2, one named condition each.
 */
@RequiredArgsConstructor
enum TransitionCondition {

	/** The caller wrote the version. */
	AUTHOR(TransitionContext::callerIsAuthor),

	/** The caller did not write the version: nobody reviews their own (8.4). */
	NOT_AUTHOR(Predicate.not(TransitionContext::callerIsAuthor)),

	/** No reviewer holds the version. */
	UNCLAIMED(Predicate.not(TransitionContext::isClaimed)),

	/**
	 * The caller is the reviewer holding the version: whoever decides holds the claim.
	 */
	CLAIMANT(TransitionContext::callerIsClaimant),

	/** The content has no open version: there is room for one (4.1). */
	NO_OPEN_VERSION(Predicate.not(TransitionContext::contentHasOpenVersion));

	private final Predicate<TransitionContext> check;

	boolean isMetBy(TransitionContext context) {
		return this.check.test(context);
	}

}
