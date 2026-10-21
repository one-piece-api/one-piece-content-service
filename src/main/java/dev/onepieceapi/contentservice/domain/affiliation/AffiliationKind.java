package dev.onepieceapi.contentservice.domain.affiliation;

/**
 * What sort of group an affiliation is (implementation plan of the Character, D10).
 * Emperors, the Seven Warlords and the Supernovas are {@link #OTHER}. "Pirate" or
 * "Marine" is never stored on a character: it is derived from the kinds of its
 * memberships.
 */
public enum AffiliationKind {

	PIRATE_CREW, NAVY, WORLD_GOVERNMENT, REVOLUTIONARY_ARMY, OTHER

}
