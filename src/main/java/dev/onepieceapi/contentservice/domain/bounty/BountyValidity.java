package dev.onepieceapi.contentservice.domain.bounty;

/**
 * Whether a bounty still stands (implementation plan of the Bounty, D3): derived, never
 * stored. Named validity, never status: the status is the editorial one of its versions.
 */
public enum BountyValidity {

	/** The latest bounty of its chain, not set aside. */
	ACTIVE,

	/** Another bounty names it as the one it replaces. */
	SUPERSEDED,

	/** Revoked, suspended or its holder dead: it overrides the other two. */
	INACTIVE;

	/** {@code INACTIVE} over {@code SUPERSEDED} over {@code ACTIVE}. */
	public static BountyValidity of(boolean inactive, boolean replaced) {
		if (inactive) {
			return INACTIVE;
		}
		return replaced ? SUPERSEDED : ACTIVE;
	}

}
