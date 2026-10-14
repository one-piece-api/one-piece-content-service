package dev.onepieceapi.contentservice.domain.story;

import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The day something came out in one country (implementation plan of the story structure,
 * S13): a volume or a chapter released, an episode broadcast. Part of the version that
 * lists it, with no lifecycle of its own.
 *
 * @param country ISO 3166-1 alpha-2 code, e.g. {@code JP}; whether it is one is for the
 * validator to say
 * @param date the calendar day in that country, with no time zone
 */
public record Release(String country, LocalDate date) {

	/** The country that stands for the original release, the one derived values read. */
	public static final String ORIGINAL_COUNTRY = "JP";

	private static final Comparator<Release> BY_DATE = Comparator
		.comparing(Release::date, Comparator.nullsLast(Comparator.naturalOrder()))
		.thenComparing(Release::country, Comparator.nullsLast(Comparator.naturalOrder()));

	/**
	 * The releases as they are stored and compared: each country without space around it
	 * and in capitals, ordered by date. A country given twice is kept twice: refusing it
	 * is the validator's, not a silent merge.
	 */
	public static List<Release> normalized(List<Release> releases) {
		return releases.stream().map(Release::normalized).sorted(BY_DATE).toList();
	}

	private Release normalized() {
		String stripped = Text.stripToNull(this.country);
		return new Release(stripped == null ? null : stripped.toUpperCase(Locale.ROOT), this.date);
	}

}
