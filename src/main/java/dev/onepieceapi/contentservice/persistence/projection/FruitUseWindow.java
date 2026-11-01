package dev.onepieceapi.contentservice.persistence.projection;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A Devil Fruit as a character online uses it (workflow 3.4.8): the character, by id and
 * romaji, and the window of its use. Each point part is null for none.
 */
public record FruitUseWindow(UUID characterContentId, String romaji, UUID discoveredChapterId, UUID discoveredEpisodeId,
		UUID endedChapterId, UUID endedEpisodeId) {

	/** The character as a block names it: {@code {id, romaji}}. */
	public Map<String, Object> named() {
		Map<String, Object> named = new LinkedHashMap<>();
		named.put("id", this.characterContentId);
		named.put("romaji", this.romaji);
		return named;
	}

	public StoryWindow window() {
		return new StoryWindow(new StoryPoint(this.discoveredChapterId, this.discoveredEpisodeId),
				new StoryPoint(this.endedChapterId, this.endedEpisodeId));
	}

}
