package dev.onepieceapi.contentservice.domain.story;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** How what an editor typed becomes what is stored and compared, and what is missing. */
class VolumeTest {

	@Test
	void theSpaceAroundTheTextsDoesNotCountAndTheNumberAndTheCoverAreKept() {
		var typed = Volume.builder()
			.number(1)
			.coverImageId("abc")
			.japaneseTitle(" ROMANCE DAWN ")
			.translations(Map.of("it", new VolumeTranslation(" L'alba ")))
			.build();

		var normalized = typed.normalized();

		assertThat(normalized.number()).isEqualTo(1);
		assertThat(normalized.coverImageId()).isEqualTo("abc");
		assertThat(normalized.japaneseTitle()).isEqualTo("ROMANCE DAWN");
		assertThat(normalized.translations()).containsExactly(entry("it", new VolumeTranslation("L'alba")));
	}

	@Test
	void aBlankTitleIsMissingAndALanguageNothingWasWrittenInIsLeftOut() {
		Map<String, VolumeTranslation> translations = new HashMap<>();
		translations.put("it", new VolumeTranslation("  "));
		translations.put("en", new VolumeTranslation("Dawn"));

		var normalized = new Volume(null, null, null, null, translations).normalized();

		assertThat(normalized.translations()).containsOnlyKeys("en");
	}

	@Test
	void theCountryIsInCapitalsAndTheReleasesAreOrderedByDate() {
		var typed = Volume.builder()
			.releases(
					List.of(new Release("us", LocalDate.of(2003, 6, 1)), new Release("JP", LocalDate.of(1997, 12, 24))))
			.build();

		assertThat(typed.normalized().releases()).containsExactly(new Release("JP", LocalDate.of(1997, 12, 24)),
				new Release("US", LocalDate.of(2003, 6, 1)));
	}

	@Test
	void theNumberIsTheNaturalKeyAndTheTitlesAreTheNamesButTwoVolumesMayShareOne() {
		var volume = Volume.builder()
			.number(0)
			.translations(Map.of("it", new VolumeTranslation("L'alba"), "en", new VolumeTranslation(null)))
			.build();

		assertThat(volume.names()).containsExactly(entry("it", "L'alba"));
		assertThat(volume.languages()).containsExactlyInAnyOrder("it", "en");
		assertThat(volume.naturalKey().orElseThrow().field()).isEqualTo("number");
		assertThat(volume.naturalKey().orElseThrow().value()).isEqualTo(0);
		assertThat(volume.uniqueNames()).isFalse();
		assertThat(new Volume(null, null, null, null, null).releases()).isEmpty();
	}

	@Test
	void anotherCoverIsAnotherVolume() {
		var volume = Volume.builder().number(1).coverImageId("abc").build();

		assertThat(volume.withCover("def").coverImageId()).isEqualTo("def");
		assertThat(volume.withCover(null).coverImageId()).isNull();
		assertThat(volume.withCover("def")).isNotEqualTo(volume);
	}

	@Test
	void theNumberAndATitlePerLanguageAreRequiredForReview() {
		var empty = new Volume(null, null, null, null, null);

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("number", "translations[en].title",
				"translations[it].title");
	}

	@Test
	void aVolumeWithoutAnyExtraIsCompleteAndATitleLeftOutIsMissing() {
		var bare = Volume.builder().number(1).translations(Map.of("it", new VolumeTranslation("L'alba"))).build();
		var withoutTitle = bare.toBuilder().translations(Map.of("it", new VolumeTranslation(null))).build();

		assertThat(bare.missingFields(List.of("it"))).isEmpty();
		assertThat(withoutTitle.missingFields(List.of("it"))).containsExactly("translations[it].title");
	}

}
