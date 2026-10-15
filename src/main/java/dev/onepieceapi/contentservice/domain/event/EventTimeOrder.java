package dev.onepieceapi.contentservice.domain.event;

import dev.onepieceapi.contentservice.domain.event.EventTime.ChapterTime;
import dev.onepieceapi.contentservice.domain.event.EventTime.WorldDateTime;
import lombok.RequiredArgsConstructor;

import java.util.UUID;
import java.util.function.Function;

/**
 * The order of two times of the world axis (implementation plan of the timeline, T3, T4):
 * every world date comes before every chapter; two chapters are ordered by their number;
 * two world dates only when one certainly ends before the other starts - a same year is
 * never out of order. A domain service: the chapter numbers are given, read by whoever
 * calls it as the rule needs them (approved for a save, online to go online).
 */
@RequiredArgsConstructor
public class EventTimeOrder {

	/** The number of a chapter by its content id; null when it has none to read. */
	private final Function<UUID, Integer> chapterNumbers;

	/**
	 * Whether {@code time} certainly comes before {@code other}; false when either is
	 * missing, or names a chapter whose number is unknown - another rule reports that.
	 */
	public boolean certainlyBefore(EventTime time, EventTime other) {
		return switch (time) {
			case null -> false;
			case WorldDateTime(WorldDate date) -> switch (other) {
				case null -> false;
				case WorldDateTime(WorldDate otherDate) -> date.certainlyBefore(otherDate);
				case ChapterTime _ -> true;
			};
			case ChapterTime(UUID chapter) ->
				other instanceof ChapterTime(UUID otherChapter) && chapterBefore(chapter, otherChapter);
		};
	}

	private boolean chapterBefore(UUID chapter, UUID other) {
		Integer number = this.chapterNumbers.apply(chapter);
		Integer otherNumber = this.chapterNumbers.apply(other);
		return number != null && otherNumber != null && number < otherNumber;
	}

}
