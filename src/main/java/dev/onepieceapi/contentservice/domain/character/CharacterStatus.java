package dev.onepieceapi.contentservice.domain.character;

/**
 * Whether a character is alive as of today (implementation plan of the Character, D4). No
 * default: whoever does not know picks {@link #UNKNOWN}.
 */
public enum CharacterStatus {

	ALIVE, DECEASED, UNKNOWN

}
