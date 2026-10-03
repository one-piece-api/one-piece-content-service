package dev.onepieceapi.contentservice.domain.workflow;

/**
 * What the transition rules answer when asked whether a caller may perform an action on a
 * version - and, when they may not, which kind of refusal it is. The two kinds are told
 * apart because they call for different things: a refused caller needs someone else to
 * act, a refused state needs the version to move first.
 */
public enum TransitionDecision {

	ALLOWED,

	/**
	 * Not for this caller: a missing permission, or a version that is not theirs to act
	 * on.
	 */
	FORBIDDEN,

	/** Not now, whoever asks: the version, or its content, is not in a state for it. */
	CONFLICT

}
