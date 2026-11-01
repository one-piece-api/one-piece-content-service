package dev.onepieceapi.contentservice.domain.species;

import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.readertime.ParagraphFixtures.discoveredAt;
import static dev.onepieceapi.contentservice.domain.readertime.ParagraphFixtures.paragraph;
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
		var species = Species.builder()
			.romaji("Gyojin")
			.translations(Map.of("it", new SpeciesTranslation("Uomo-pesce")))
			.description(paragraph("en", "A race"))
			.build();

		assertThat(species.naturalKey().field()).isEqualTo("romaji");
		assertThat(species.naturalKeyText()).isEqualTo("Gyojin");
		assertThat(species.names()).containsExactly(entry("it", "Uomo-pesce"));
		assertThat(species.uniqueNames()).isTrue();
		assertThat(species.languages()).containsExactly("en", "it");
	}

	@Test
	void theSpaceAroundATextDoesNotCountAndABlankOneIsMissing() {
		var typed = Species.builder()
			.romaji("  Gyojin ")
			.translations(Map.of("it", new SpeciesTranslation(" Uomo-pesce ")))
			.imageId("img")
			.firstAppearance(StoryPoint.ofChapter(CHAPTER))
			.description(paragraph(Map.of("it", "\n Razza ", "en", " ")))
			.build();

		var normalized = typed.normalized();

		assertThat(normalized.romaji()).isEqualTo("Gyojin");
		assertThat(normalized.translations()).containsExactly(entry("it", new SpeciesTranslation("Uomo-pesce")));
		assertThat(normalized.description()).containsExactly(new Paragraph(Map.of("it", "Razza"), StoryWindow.NONE));
		assertThat(normalized.imageId()).isEqualTo("img");
		assertThat(normalized.firstAppearance()).isEqualTo(StoryPoint.ofChapter(CHAPTER));
		assertThat(Species.builder().romaji("   ").build().normalized().romaji()).isNull();
	}

	@Test
	void aLanguageNothingWasWrittenInIsLeftOut() {
		Map<String, SpeciesTranslation> translations = new HashMap<>();
		translations.put("it", new SpeciesTranslation("Uomo-pesce"));
		translations.put("en", new SpeciesTranslation("  "));

		var normalized = Species.builder().romaji("Gyojin").translations(translations).build().normalized();

		assertThat(normalized.translations()).containsOnlyKeys("it");
		assertThat(normalized.translationIn("en")).isEqualTo(SpeciesTranslation.NONE);
	}

	@Test
	void whatIsRequiredForReviewIsNamedInOrder() {
		var empty = Species.builder().build();

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("romaji", "firstAppearance",
				"translations[en].name", "translations[it].name", "description");
	}

	@Test
	void theImageIsNeverMissingAndAnEpisodeAloneIsAFirstAppearance() {
		var episode = StoryPoint.ofEpisode(CHAPTER);
		var complete = Species.builder()
			.romaji("Gyojin")
			.translations(Map.of("it", new SpeciesTranslation("Uomo-pesce")))
			.firstAppearance(episode)
			.description(discoveredAt(episode, Map.of("it", "Razza")))
			.build();

		assertThat(complete.missingFields(List.of("it"))).isEmpty();
	}

	@Test
	void anotherImageOrFirstAppearanceOrParagraphMakesAnotherVersion() {
		var base = Species.builder().romaji("Gyojin").build();

		assertThat(base).isEqualTo(base.toBuilder().firstAppearance(StoryPoint.NONE).description(List.of()).build())
			.isNotEqualTo(base.withImage("img"))
			.isNotEqualTo(base.toBuilder().firstAppearance(StoryPoint.ofChapter(CHAPTER)).build())
			.isNotEqualTo(base.toBuilder().description(paragraph("it", "Razza")).build());
	}

}
