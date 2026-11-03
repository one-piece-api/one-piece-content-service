package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.readertime.RevealedName;

import java.util.Map;
import java.util.UUID;

/**
 * A Character as another content points to it (implementation plan of the Character, C6):
 * its content id, and what to call it as it was last approved - the character as of
 * today, never a copy kept in the pointing version.
 *
 * @param names the name per language code, for the languages that have one
 * @param gender as the work presents the character; it gives the labels of a relative
 */
public record CharacterReference(UUID id, String romaji, Map<String, String> names, Gender gender) {

	/** The same character called as the reader knows it at some point (3.7.1). */
	public CharacterReference named(RevealedName name) {
		return new CharacterReference(this.id, name.romaji(), name.names(), this.gender);
	}

}
