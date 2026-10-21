package dev.onepieceapi.contentservice.domain.species;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * How what an editor typed becomes what is stored and compared, and what a species needs
 * to be submitted.
 */
class SpeciesTest {

	private static final UUID CHAPTER = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	@Test
	void theKeyIsTheRomajiAndTheNamesAreUniquePerLanguage() {
		var species = new Species("Gyojin",
				Map.of("it", new SpeciesTranslation("Uomo-pesce", null), "en", new SpeciesTranslation(null, "A race")),
				null, StoryPoint.NONE);

		assertThat(species.naturalKey().field()).isEqualTo("romaji");
		assertThat(species.naturalKeyText()).isEqualTo("Gyojin");
		assertThat(species.names()).containsExactly(entry("it", "Uomo-pesce"));
		assertThat(species.uniqueNames()).isTrue();
		assertThat(species.languages()).containsExactlyInAnyOrder("it", "en");
	}

	@Test
	void theSpaceAroundATextDoesNotCountAndABlankOneIsMissing() {
		var typed = new Species("  Kaigun ", Map.of("it", new SpeciesTranslation(" Marina ", "\n Forze ")), "img",
				StoryPoint.ofChapter(CHAPTER));

		var normalized = typed.normalized();

		assertThat(normalized.romaji()).isEqualTo("Kaigun");
		assertThat(normalized.translations()).containsExactly(entry("it", new SpeciesTranslation("Marina", "Forze")));
		assertThat(normalized.imageId()).isEqualTo("img");
		assertThat(normalized.firstAppearance()).isEqualTo(StoryPoint.ofChapter(CHAPTER));
		assertThat(new Species("   ", Map.of(), null, null).normalized().romaji()).isNull();
	}

	@Test
	void aLanguageNothingWasWrittenInIsLeftOut() {
		Map<String, SpeciesTranslation> translations = new HashMap<>();
		translations.put("it", new SpeciesTranslation("Marina", null));
		translations.put("en", new SpeciesTranslation("  ", " "));

		var normalized = new Species("Kaigun", translations, null, null).normalized();

		assertThat(normalized.translations()).containsOnlyKeys("it");
		assertThat(normalized.translationIn("en")).isEqualTo(SpeciesTranslation.NONE);
	}

	@Test
	void whatIsRequiredForReviewIsNamedInOrder() {
		var empty = new Species(null, Map.of(), null, null);

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("romaji", "firstAppearance",
				"translations[en].name", "translations[en].description", "translations[it].name",
				"translations[it].description");
	}

	@Test
	void theImageIsNeverMissingAndAnEpisodeAloneIsAFirstAppearance() {
		var complete = new Species("Kaigun", Map.of("it", new SpeciesTranslation("Marina", "Forze")), null,
				StoryPoint.ofEpisode(CHAPTER));

		assertThat(complete.missingFields(List.of("it"))).isEmpty();
	}

	@Test
	void anotherImageOrFirstAppearanceMakesAnotherVersion() {
		var base = new Species("Kaigun", Map.of(), null, null);

		assertThat(base).isEqualTo(new Species("Kaigun", Map.of(), null, StoryPoint.NONE))
			.isNotEqualTo(base.withImage("img"))
			.isNotEqualTo(new Species("Kaigun", Map.of(), null, StoryPoint.ofChapter(CHAPTER)));
	}

}
