package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.readertime.Windowed;

import java.util.UUID;

/**
 * A relative of a character (implementation plan of the Character, D13), with its window
 * on the reader axis (workflow 3.4.9), the same on both sides of the link. Part of the
 * character's version, in the order it is shown.
 *
 * @param relativeContentId the content id of the relative
 * @param kind what the relative is to this character
 */
public record Relative(UUID relativeContentId, RelativeKind kind, StoryWindow window) implements Windowed {

	public Relative {
		window = StoryWindow.orNone(window);
	}

}
