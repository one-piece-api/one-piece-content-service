package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * The localized fields of a Character in one language
 * (docs/user-flows/content-editorial-workflow.md 3.1.11). Either may still be missing
 * while the version is a draft.
 */
public record CharacterTranslation(String name, String description) {

	/** A language nothing was written in. */
	static final CharacterTranslation NONE = new CharacterTranslation(null, null);

	CharacterTranslation normalized() {
		return new CharacterTranslation(Text.stripToNull(this.name), Text.stripToNull(this.description));
	}

	/** Nothing written in this language. */
	boolean isEmpty() {
		return Stream.of(this.name, this.description).allMatch(Objects::isNull);
	}

}
