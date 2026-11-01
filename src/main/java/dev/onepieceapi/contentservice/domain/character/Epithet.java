package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.readertime.Windowed;
import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.Map;

/**
 * A descriptive nickname of a character, translated per language - "Gatta ladra" / "Cat
 * Burglar" (implementation plan of the Character, D6), with its window on the reader axis
 * (workflow 3.7.2). Part of the character's version, in the order it is shown.
 *
 * @param text per language code, for the languages it is written in
 */
public record Epithet(Map<String, String> text, StoryWindow window) implements Windowed {

	public Epithet {
		text = text == null ? Map.of() : Map.copyOf(text);
		window = StoryWindow.orNone(window);
	}

	/** Every text without the space around it; a language left blank is not written. */
	Epithet normalized() {
		return new Epithet(Text.written(this.text), this.window);
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
