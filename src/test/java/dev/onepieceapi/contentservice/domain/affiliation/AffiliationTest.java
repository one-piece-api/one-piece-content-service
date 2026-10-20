package dev.onepieceapi.contentservice.domain.affiliation;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * How what an editor typed becomes what is stored and compared, and what an affiliation
 * needs to be submitted.
 */
class AffiliationTest {

	private static final UUID CHAPTER = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	@Test
	void theKeyIsTheRomajiAndTheNamesAreUniquePerLanguage() {
		var affiliation = new Affiliation("Mugiwara no Ichimi",
				Map.of("it", new AffiliationTranslation("Pirati di Cappello di Paglia", null), "en",
						new AffiliationTranslation(null, "A crew")),
				null, StoryPoint.NONE);

		assertThat(affiliation.naturalKey().field()).isEqualTo("romaji");
		assertThat(affiliation.naturalKeyText()).isEqualTo("Mugiwara no Ichimi");
		assertThat(affiliation.names()).containsExactly(entry("it", "Pirati di Cappello di Paglia"));
		assertThat(affiliation.uniqueNames()).isTrue();
		assertThat(affiliation.languages()).containsExactlyInAnyOrder("it", "en");
	}

	@Test
	void theSpaceAroundATextDoesNotCountAndABlankOneIsMissing() {
		var typed = new Affiliation("  Kaigun ", Map.of("it", new AffiliationTranslation(" Marina ", "\n Forze ")),
				"img", StoryPoint.ofChapter(CHAPTER));

		var normalized = typed.normalized();

		assertThat(normalized.romaji()).isEqualTo("Kaigun");
		assertThat(normalized.translations())
			.containsExactly(entry("it", new AffiliationTranslation("Marina", "Forze")));
		assertThat(normalized.imageId()).isEqualTo("img");
		assertThat(normalized.firstAppearance()).isEqualTo(StoryPoint.ofChapter(CHAPTER));
		assertThat(new Affiliation("   ", Map.of(), null, null).normalized().romaji()).isNull();
	}

	@Test
	void aLanguageNothingWasWrittenInIsLeftOut() {
		Map<String, AffiliationTranslation> translations = new HashMap<>();
		translations.put("it", new AffiliationTranslation("Marina", null));
		translations.put("en", new AffiliationTranslation("  ", " "));

		var normalized = new Affiliation("Kaigun", translations, null, null).normalized();

		assertThat(normalized.translations()).containsOnlyKeys("it");
		assertThat(normalized.translationIn("en")).isEqualTo(AffiliationTranslation.NONE);
	}

	@Test
	void whatIsRequiredForReviewIsNamedInOrder() {
		var empty = new Affiliation(null, Map.of(), null, null);

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("romaji", "firstAppearance",
				"translations[en].name", "translations[en].description", "translations[it].name",
				"translations[it].description");
	}

	@Test
	void theImageIsNeverMissingAndAnEpisodeAloneIsAFirstAppearance() {
		var complete = new Affiliation("Kaigun", Map.of("it", new AffiliationTranslation("Marina", "Forze")), null,
				StoryPoint.ofEpisode(CHAPTER));

		assertThat(complete.missingFields(List.of("it"))).isEmpty();
	}

	@Test
	void anotherImageOrFirstAppearanceMakesAnotherVersion() {
		var base = new Affiliation("Kaigun", Map.of(), null, null);

		assertThat(base).isEqualTo(new Affiliation("Kaigun", Map.of(), null, StoryPoint.NONE))
			.isNotEqualTo(base.withImage("img"))
			.isNotEqualTo(new Affiliation("Kaigun", Map.of(), null, StoryPoint.ofChapter(CHAPTER)));
	}

}
