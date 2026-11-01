package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.workflow.Text;

/**
 * The localized field of a Character in one language
 * (docs/user-flows/content-editorial-workflow.md 3.1.11): its name, which may still be
 * missing while the version is a draft. The description is a list of paragraphs (3.7.3).
 */
public record CharacterTranslation(String name) {

	/** A language nothing was written in. */
	static final CharacterTranslation NONE = new CharacterTranslation(null);

	CharacterTranslation normalized() {
		return new CharacterTranslation(Text.stripToNull(this.name));
	}

	/** Nothing written in this language. */
	boolean isEmpty() {
		return this.name == null;
	}

}
