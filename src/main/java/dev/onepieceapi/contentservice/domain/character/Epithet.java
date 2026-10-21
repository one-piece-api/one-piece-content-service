package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.Map;
import java.util.TreeMap;

/**
 * A descriptive nickname of a character, translated per language - "Gatta ladra" / "Cat
 * Burglar" (implementation plan of the Character, D6). Part of the character's version,
 * in the order it is shown.
 *
 * @param text per language code, for the languages it is written in
 */
public record Epithet(Map<String, String> text) {

	public Epithet {
		text = text == null ? Map.of() : Map.copyOf(text);
	}

	/** Every text without the space around it; a language left blank is not written. */
	Epithet normalized() {
		Map<String, String> written = new TreeMap<>();
		this.text.forEach((language, value) -> {
			String normalized = Text.stripToNull(value);
			if (normalized != null) {
				written.put(language, normalized);
			}
		});
		return new Epithet(written);
	}

	/** Nothing written in any language: an entry a save refuses. */
	public boolean isEmpty() {
		return this.text.isEmpty();
	}

	/** What it says in this language; null when nothing is written in it. */
	public String textIn(String language) {
		return this.text.get(language);
	}

}
