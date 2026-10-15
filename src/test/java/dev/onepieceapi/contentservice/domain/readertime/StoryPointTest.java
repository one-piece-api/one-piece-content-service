package dev.onepieceapi.contentservice.domain.readertime;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A point of the reader axis: what is none, and when two are the same. */
class StoryPointTest {

	private static final UUID ROMANCE_DAWN = UUID.fromString("7d3b2a1c-0e9f-4a8b-8c7d-6e5f4a3b2c1d");

	@Test
	void aPointWithoutAChapterIsMissingAndIsNone() {
		assertThat(StoryPoint.NONE.isMissing()).isTrue();
		assertThat(new StoryPoint(null)).isEqualTo(StoryPoint.NONE);
		assertThat(new StoryPoint(ROMANCE_DAWN).isMissing()).isFalse();
	}

	@Test
	void nothingGivenIsNoneAndAPointGivenIsKept() {
		var point = new StoryPoint(ROMANCE_DAWN);

		assertThat(StoryPoint.orNone(null)).isSameAs(StoryPoint.NONE);
		assertThat(StoryPoint.orNone(point)).isSameAs(point);
	}

	@Test
	void twoPointsInTheSameChapterAreTheSame() {
		assertThat(new StoryPoint(ROMANCE_DAWN)).isEqualTo(new StoryPoint(ROMANCE_DAWN))
			.hasSameHashCodeAs(new StoryPoint(ROMANCE_DAWN))
			.isNotEqualTo(new StoryPoint(UUID.randomUUID()));
	}

}
