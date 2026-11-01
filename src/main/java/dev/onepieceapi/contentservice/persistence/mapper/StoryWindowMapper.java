package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.persistence.entity.StoryWindowEmbeddable;
import lombok.experimental.UtilityClass;

/** A window on the reader axis between its domain form and its four columns (V38). */
@UtilityClass
public class StoryWindowMapper {

	/** Null when no point is chosen: what the columns read back as anyway. */
	public StoryWindowEmbeddable toEmbeddable(StoryWindow window) {
		if (window.discovered().isMissing() && window.ended().isMissing()) {
			return null;
		}
		return new StoryWindowEmbeddable(window.discovered().chapterContentId(), window.discovered().episodeContentId(),
				window.ended().chapterContentId(), window.ended().episodeContentId());
	}

	/** Null columns are a window with nothing chosen. */
	public StoryWindow toDomain(StoryWindowEmbeddable window) {
		if (window == null) {
			return StoryWindow.NONE;
		}
		return new StoryWindow(new StoryPoint(window.getDiscoveredChapterId(), window.getDiscoveredEpisodeId()),
				new StoryPoint(window.getEndedChapterId(), window.getEndedEpisodeId()));
	}

}
