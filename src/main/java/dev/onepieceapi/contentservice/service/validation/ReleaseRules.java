package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.story.Release;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What the days of release or broadcast of a volume, a chapter or an episode must meet
 * (implementation plan of the story structure, S13): a country of the ISO 3166-1 alpha-2
 * list the JDK knows, and one day per country. Every breach is reported, named as a
 * request names it: {@code releases[1].country}.
 */
@UtilityClass
public class ReleaseRules {

	private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

	private static final String COUNTRY_FIELD = "%s[%d].country";

	private static final String UNKNOWN_COUNTRY = "must be an ISO 3166-1 alpha-2 country code";

	private static final String REPEATED_COUNTRY = "has more than one date for the same country";

	/**
	 * @param field the list as a request names it, e.g. {@code releases}
	 * @param releases as written, already normalized
	 */
	public List<FieldViolation> violationsOf(String field, List<Release> releases) {
		List<FieldViolation> violations = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (int index = 0; index < releases.size(); index++) {
			String country = releases.get(index).country();
			String name = COUNTRY_FIELD.formatted(field, index);
			if (country == null || !COUNTRIES.contains(country)) {
				violations.add(new FieldViolation(name, UNKNOWN_COUNTRY));
			}
			else if (!seen.add(country)) {
				violations.add(new FieldViolation(name, REPEATED_COUNTRY));
			}
		}
		return violations;
	}

}
