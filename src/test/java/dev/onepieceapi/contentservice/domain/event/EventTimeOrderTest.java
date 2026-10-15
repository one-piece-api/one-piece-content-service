package dev.onepieceapi.contentservice.domain.event;

import dev.onepieceapi.contentservice.domain.event.EventTime.ChapterTime;
import dev.onepieceapi.contentservice.domain.event.EventTime.WorldDateTime;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The order of the world axis (implementation plan of the timeline, T3, T4): world dates
 * before chapters, chapters by number, world dates only when certainly apart.
 */
class EventTimeOrderTest {

	private static final UUID CHAPTER_1 = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID CHAPTER_956 = UUID.fromString("5a1d9c2e-77b4-4d3a-8e0f-2c6b1a9d4e22");

	private static final UUID UNKNOWN = UUID.fromString("9e8d7c6b-5a49-4382-9170-6f5e4d3c2b1a");

	private final EventTimeOrder order = new EventTimeOrder(Map.of(CHAPTER_1, 1, CHAPTER_956, 956)::get);

	@Test
	void aWorldDateComesBeforeEveryChapterAndNeverAfterOne() {
		assertThat(this.order.certainlyBefore(year(1522), chapter(CHAPTER_1))).isTrue();
		assertThat(this.order.certainlyBefore(chapter(CHAPTER_1), year(1500))).isFalse();
	}

	@Test
	void twoChaptersAreOrderedByTheirNumber() {
		assertThat(this.order.certainlyBefore(chapter(CHAPTER_1), chapter(CHAPTER_956))).isTrue();
		assertThat(this.order.certainlyBefore(chapter(CHAPTER_956), chapter(CHAPTER_1))).isFalse();
		assertThat(this.order.certainlyBefore(chapter(CHAPTER_1), chapter(CHAPTER_1))).isFalse();
	}

	@Test
	void aChapterWhoseNumberIsUnknownIsNeverOutOfOrder() {
		assertThat(this.order.certainlyBefore(chapter(UNKNOWN), chapter(CHAPTER_956))).isFalse();
		assertThat(this.order.certainlyBefore(chapter(CHAPTER_956), chapter(UNKNOWN))).isFalse();
	}

	@Test
	void twoWorldDatesAreOrderedOnlyWhenOneEndsBeforeTheOtherStarts() {
		var voidCentury = new WorldDateTime(new WorldDate(700, 800, true));
		assertThat(this.order.certainlyBefore(voidCentury, year(1498))).isTrue();
		assertThat(this.order.certainlyBefore(year(1498), voidCentury)).isFalse();
		assertThat(this.order.certainlyBefore(year(1500), year(1500))).isFalse();
		assertThat(this.order.certainlyBefore(new WorldDateTime(new WorldDate(1498, 1501, false)), year(1500)))
			.isFalse();
	}

	@Test
	void aMissingTimeIsNeverOutOfOrder() {
		assertThat(this.order.certainlyBefore(null, chapter(CHAPTER_1))).isFalse();
		assertThat(this.order.certainlyBefore(year(1500), null)).isFalse();
	}

	private static EventTime chapter(UUID chapter) {
		return new ChapterTime(chapter);
	}

	private static EventTime year(int year) {
		return new WorldDateTime(new WorldDate(year, null, false));
	}

}
