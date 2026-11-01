package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.readertime.Windowed;

import java.util.UUID;

/**
 * A species of a character (implementation plan of the Character, D8), with its window on
 * the reader axis (workflow 3.7.2). Part of the character's version, in the order it is
 * shown.
 *
 * @param speciesContentId the content id of the species
 */
public record SpeciesEntry(UUID speciesContentId, StoryWindow window) implements Windowed {

	public SpeciesEntry {
		window = StoryWindow.orNone(window);
	}

}
