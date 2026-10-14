package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.story.Release;
import dev.onepieceapi.exception.web.FieldViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the days of release or broadcast must meet (implementation plan of the story
 * structure, S13): a known country, one day per country, every breach named as a request
 * names it.
 */
class ReleaseRulesTest {

	private static final LocalDate DAY = LocalDate.of(1997, 12, 24);

	@Test
	void noReleaseBreaksNothing() {
		assertThat(ReleaseRules.violationsOf("releases", List.of())).isEmpty();
	}

	@Test
	void oneDayPerKnownCountryIsFine() {
		var releases = Release.normalized(List.of(new Release("JP", DAY), new Release("it", DAY)));

		assertThat(ReleaseRules.violationsOf("releases", releases)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "ZZ", "XX", "JPN", "J", "1A" })
	void aCountryOutsideTheIsoListIsRefusedNamingItsPosition(String country) {
		var releases = List.of(new Release("JP", DAY), new Release(country, DAY));

		assertThat(ReleaseRules.violationsOf("broadcasts", releases))
			.containsExactly(new FieldViolation("broadcasts[1].country", "must be an ISO 3166-1 alpha-2 country code"));
	}

	@Test
	void aMissingCountryIsRefused() {
		var violations = ReleaseRules.violationsOf("releases", List.of(new Release(null, DAY)));

		assertThat(violations).extracting(FieldViolation::field).containsExactly("releases[0].country");
	}

	@Test
	void aCapitalCountryOnlyIsKnownSoTheNormalizationComesFirst() {
		assertThat(ReleaseRules.violationsOf("releases", List.of(new Release("jp", DAY)))).hasSize(1);
		assertThat(ReleaseRules.violationsOf("releases", Release.normalized(List.of(new Release("jp", DAY)))))
			.isEmpty();
	}

	@Test
	void aCountryWithTwoDatesIsRefusedAtTheSecondOne() {
		var releases = Release
			.normalized(List.of(new Release("JP", DAY), new Release(" jp ", DAY.plusDays(7)), new Release("IT", DAY)));

		assertThat(ReleaseRules.violationsOf("releases", releases))
			.containsExactly(new FieldViolation("releases[2].country", "has more than one date for the same country"));
	}

	@Test
	void everyBreachIsReportedTogether() {
		var releases = List.of(new Release("ZZ", DAY), new Release("JP", DAY), new Release("JP", DAY));

		assertThat(ReleaseRules.violationsOf("releases", releases)).extracting(FieldViolation::field)
			.containsExactly("releases[0].country", "releases[2].country");
	}

}
