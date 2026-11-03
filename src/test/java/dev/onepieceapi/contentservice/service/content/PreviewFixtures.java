package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.readertime.ReaderPosition;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * A story for preview tests: chapter and episode N have a content id of their own and
 * number N, as approved; no database needed.
 */
public final class PreviewFixtures {

	private static final int LAST = 1200;

	private static final long CHAPTERS = 1;

	private static final long EPISODES = 2;

	private PreviewFixtures() {
	}

	public static UUID chapter(int number) {
		return new UUID(CHAPTERS, number);
	}

	public static UUID episode(int number) {
		return new UUID(EPISODES, number);
	}

	public static StoryPoint atChapter(int number) {
		return StoryPoint.ofChapter(chapter(number));
	}

	public static StoryPoint atEpisode(int number) {
		return StoryPoint.ofEpisode(episode(number));
	}

	public static StoryPoint at(int chapter, int episode) {
		return new StoryPoint(chapter(chapter), episode(episode));
	}

	public static StoryWindow fromChapter(int number) {
		return StoryWindow.from(atChapter(number));
	}

	public static StoryWindow chapters(int discovered, int ended) {
		return new StoryWindow(atChapter(discovered), atChapter(ended));
	}

	/** Every chapter and episode of the story, numbered. */
	public static StoryNumbers numbers() {
		Map<UUID, Integer> chapters = new HashMap<>();
		Map<UUID, Integer> episodes = new HashMap<>();
		IntStream.rangeClosed(1, LAST).forEach(number -> {
			chapters.put(chapter(number), number);
			episodes.put(episode(number), number);
		});
		return new StoryNumbers(chapters, episodes);
	}

	public static PreviewContext context(ReaderPosition position, Map<UUID, StoryPoint> onlineTargets) {
		return new PreviewContext(position, numbers(), onlineTargets);
	}

	/**
	 * The previews' helper on this story, as a spy: a test stubs {@code online} with the
	 * items online it needs.
	 */
	public static ReaderPreviews previews() {
		StoryNumbering numbering = mock(StoryNumbering.class);
		when(numbering.numbersOf(any(), any())).thenReturn(numbers());
		return spy(new ReaderPreviews(numbering));
	}

}
