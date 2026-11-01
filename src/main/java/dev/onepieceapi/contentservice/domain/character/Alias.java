package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.readertime.Windowed;

/**
 * Another name of a character, the same in every language - "Sogeking" (implementation
 * plan of the Character, D6), with its window on the reader axis (workflow 3.7.2). Part
 * of the character's version, in the order it is shown.
 *
 * @param name never null; a blank one stays, for the save to refuse it
 */
public record Alias(String name, StoryWindow window) implements Windowed {

	public Alias {
		name = name == null ? "" : name;
		window = StoryWindow.orNone(window);
	}

	Alias normalized() {
		return new Alias(this.name.strip(), this.window);
	}

}
