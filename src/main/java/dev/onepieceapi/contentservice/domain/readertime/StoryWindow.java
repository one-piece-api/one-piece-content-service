package dev.onepieceapi.contentservice.domain.readertime;

import java.util.stream.Stream;

/**
 * When the reader learns that an entry starts and stops being true
 * (docs/user-flows/content-editorial-workflow.md 3.7.2): a {@code discovered} point,
 * required to submit, and an optional {@code ended} one. The points name chapters and
 * episodes by content; the library {@code one-piece-reader-time} compares them once their
 * numbers are known.
 *
 * @param discovered where the reader learns it; {@link StoryPoint#NONE} while not chosen
 * @param ended where the reader learns it is over; {@link StoryPoint#NONE} for none
 */
public record StoryWindow(StoryPoint discovered, StoryPoint ended) {

	/** Nothing chosen yet. */
	public static final StoryWindow NONE = new StoryWindow(StoryPoint.NONE, StoryPoint.NONE);

	public StoryWindow {
		discovered = StoryPoint.orNone(discovered);
		ended = StoryPoint.orNone(ended);
	}

	/** A window with no end. */
	public static StoryWindow from(StoryPoint discovered) {
		return new StoryWindow(discovered, StoryPoint.NONE);
	}

	/** The window given, or {@link #NONE} for none. */
	public static StoryWindow orNone(StoryWindow window) {
		return window == null ? NONE : window;
	}

	/** Current without a filter: no end on any axis (3.7.2). */
	public boolean isOpen() {
		return this.ended.isMissing();
	}

	/** Both points, discovered first. */
	public Stream<StoryPoint> points() {
		return Stream.of(this.discovered, this.ended);
	}

}
