package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.readertime.Point;
import dev.onepieceapi.readertime.RevealWindow;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The numbers of some chapters and episodes, by content: what turns a point naming them
 * into the numbers the library {@code one-piece-reader-time} compares. Loaded by
 * {@link StoryNumbering}. A part with no number is not seen on its axis.
 *
 * @param chapters the number of each chapter, by content id
 * @param episodes the number of each episode, by content id
 */
public record StoryNumbers(Map<UUID, Integer> chapters, Map<UUID, Integer> episodes) {

	public StoryNumbers {
		chapters = Map.copyOf(chapters);
		episodes = Map.copyOf(episodes);
	}

	public Point pointOf(StoryPoint point) {
		return new Point(numberOf(this.chapters, point.chapterContentId()),
				numberOf(this.episodes, point.episodeContentId()));
	}

	public RevealWindow windowOf(StoryWindow window) {
		return new RevealWindow(pointOf(window.discovered()), pointOf(window.ended()));
	}

	/** Whether every chapter and episode of these points has a number. */
	public boolean coverAll(Collection<StoryPoint> points) {
		return this.chapters.keySet().containsAll(idsOf(points, StoryPoint::chapterContentId))
				&& this.episodes.keySet().containsAll(idsOf(points, StoryPoint::episodeContentId));
	}

	/** The chapters, or the episodes, these points name. */
	static Set<UUID> idsOf(Collection<StoryPoint> points, Function<StoryPoint, UUID> part) {
		return points.stream().map(part).filter(Objects::nonNull).collect(Collectors.toSet());
	}

	private static Integer numberOf(Map<UUID, Integer> numbers, UUID contentId) {
		return contentId == null ? null : numbers.get(contentId);
	}

}
