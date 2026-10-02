package dev.onepieceapi.contentservice.domain.workflow;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;

class ContentFilterTest {

	private static final Set<VersionStatus> SEEN_BY_A_REVIEWER = EnumSet.of(IN_REVIEW, PUBLISHED);

	@Test
	void noFilterFiltersOnNothing() {
		assertThat(ContentFilter.none()).isEqualTo(new ContentFilter(null, null, null, null));
	}

	@Test
	void theAuthorFilterIsByUsernameAndNothingElse() {
		assertThat(ContentFilter.authoredBy("nami")).isEqualTo(new ContentFilter(null, null, "nami", null));
	}

	@Test
	void withoutAStatusEveryVisibleStatusIsListed() {
		assertThat(filter(null, null, null).statusesAmong(SEEN_BY_A_REVIEWER)).isEqualTo(SEEN_BY_A_REVIEWER);
	}

	@Test
	void aVisibleStatusNarrowsTheListToItself() {
		assertThat(filter(PUBLISHED, null, null).statusesAmong(SEEN_BY_A_REVIEWER)).containsExactly(PUBLISHED);
	}

	@Test
	void aStatusTheCallerDoesNotSeeListsNothing() {
		assertThat(filter(DRAFT, null, null).statusesAmong(SEEN_BY_A_REVIEWER)).isEmpty();
	}

	@Test
	void theTextIsTrimmedAndABlankOneIsNoFilter() {
		assertThat(filter(null, "  zoan ", null).text()).contains("zoan");
		assertThat(filter(null, "   ", null).text()).isEmpty();
		assertThat(filter(null, null, null).text()).isEmpty();
	}

	@Test
	void updatedTodayStartsAtMidnightUtc() {
		var clock = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneId.of("UTC"));

		assertThat(filter(null, null, 0).updatedSince(clock)).contains(Instant.parse("2026-10-01T00:00:00Z"));
		assertThat(filter(null, null, 7).updatedSince(clock)).contains(Instant.parse("2026-09-24T00:00:00Z"));
		assertThat(filter(null, null, null).updatedSince(clock)).isEmpty();
	}

	@Test
	void todayIsTheUtcDayWhateverTheZoneOfTheClock() {
		// 01:30 in Rome on the 2nd is still the 1st in UTC.
		var romeClock = Clock.fixed(Instant.parse("2026-10-01T23:30:00Z"), ZoneId.of("Europe/Rome"));

		assertThat(filter(null, null, 0).updatedSince(romeClock)).contains(Instant.parse("2026-10-01T00:00:00Z"));
	}

	private static ContentFilter filter(VersionStatus status, String query, Integer updatedWithinDays) {
		return new ContentFilter(status, query, null, updatedWithinDays);
	}

}
