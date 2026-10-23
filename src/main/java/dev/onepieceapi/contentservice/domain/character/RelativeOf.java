package dev.onepieceapi.contentservice.domain.character;

import java.util.UUID;

/**
 * A character online that names another one as its relative, as the other one reads it
 * (implementation plan of the Character, D13): the link is stored by this character, and
 * the other side shows it through the inverse kind. Never part of a version.
 *
 * @param character the character holding the link
 * @param kind what that character is to the other: the inverse of the stored kind
 */
public record RelativeOf(CharacterReference character, RelativeKind kind) {

	public UUID characterId() {
		return this.character.id();
	}

	public String romaji() {
		return this.character.romaji();
	}

}
