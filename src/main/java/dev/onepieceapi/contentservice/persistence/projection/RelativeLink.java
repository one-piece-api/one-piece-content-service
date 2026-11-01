package dev.onepieceapi.contentservice.persistence.projection;

import dev.onepieceapi.contentservice.domain.character.RelativeKind;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;

import java.util.UUID;

/**
 * A link stored by a character online: the character holding it, the stored kind and the
 * window, the same on both sides (workflow 3.4.9). Each point part is null for none.
 */
public record RelativeLink(UUID holderContentId, RelativeKind kind, UUID discoveredChapterId, UUID discoveredEpisodeId,
		UUID endedChapterId, UUID endedEpisodeId) {

	public StoryWindow window() {
		return new StoryWindow(new StoryPoint(this.discoveredChapterId, this.discoveredEpisodeId),
				new StoryPoint(this.endedChapterId, this.endedEpisodeId));
	}

}
