package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.readertime.Windowed;

import java.util.UUID;

/**
 * A Devil Fruit a character ate (implementation plan of the Character, D12), with its
 * window on the reader axis (workflow 3.4.8): Ace's Mera Mera ends at Marineford, Sabo's
 * starts at Dressrosa. Former when the window has an end, never stored. Part of the
 * character's version, in the order it is shown.
 *
 * @param devilFruitContentId the content id of the fruit
 */
public record FruitUse(UUID devilFruitContentId, StoryWindow window) implements Windowed {

	public FruitUse {
		window = StoryWindow.orNone(window);
	}

}
