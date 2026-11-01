package dev.onepieceapi.contentservice.domain.affiliation;

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
 * How what an editor typed becomes what is stored and compared, and what an affiliation
 * needs to be submitted.
 */
class AffiliationTest {

	private static final UUID CHAPTER = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID LATER = UUID.fromString("1a7d4f6b-5c8e-4d2f-8b69-4e3c2d1f8a22");

	@Test
	void theKeyIsTheRomajiAndTheNamesAreUniquePerLanguage() {
		var affiliation = Affiliation.builder()
			.romaji("Mugiwara no Ichimi")
			.translations(Map.of("it", new AffiliationTranslation("Pirati di Cappello di Paglia")))
			.description(paragraph("en", "A crew"))
			.build();

		assertThat(affiliation.naturalKey().field()).isEqualTo("romaji");
		assertThat(affiliation.naturalKeyText()).isEqualTo("Mugiwara no Ichimi");
		assertThat(affiliation.names()).containsExactly(entry("it", "Pirati di Cappello di Paglia"));
		assertThat(affiliation.uniqueNames()).isTrue();
		assertThat(affiliation.languages()).containsExactly("en", "it");
	}

	@Test
	void theSpaceAroundATextDoesNotCountAndABlankOneIsMissing() {
		var typed = Affiliation.builder()
			.romaji("  Kaigun ")
			.translations(Map.of("it", new AffiliationTranslation(" Marina ")))
			.imageId("img")
			.firstAppearance(StoryPoint.ofChapter(CHAPTER))
			.description(paragraph(Map.of("it", "\n Forze ", "en", "  ")))
			.build();

		var normalized = typed.normalized();

		assertThat(normalized.romaji()).isEqualTo("Kaigun");
		assertThat(normalized.translations()).containsExactly(entry("it", new AffiliationTranslation("Marina")));
		assertThat(normalized.description()).containsExactly(new Paragraph(Map.of("it", "Forze"), StoryWindow.NONE));
		assertThat(normalized.imageId()).isEqualTo("img");
		assertThat(normalized.firstAppearance()).isEqualTo(StoryPoint.ofChapter(CHAPTER));
		assertThat(Affiliation.builder().romaji("   ").build().normalized().romaji()).isNull();
	}

	@Test
	void aLanguageNothingWasWrittenInIsLeftOut() {
		Map<String, AffiliationTranslation> translations = new HashMap<>();
		translations.put("it", new AffiliationTranslation("Marina"));
		translations.put("en", new AffiliationTranslation("  "));

		var normalized = Affiliation.builder().romaji("Kaigun").translations(translations).build().normalized();

		assertThat(normalized.translations()).containsOnlyKeys("it");
		assertThat(normalized.translationIn("en")).isEqualTo(AffiliationTranslation.NONE);
	}

	@Test
	void whatIsRequiredForReviewIsNamedInOrder() {
		var empty = Affiliation.builder().build();

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("romaji", "kind", "firstAppearance",
				"translations[en].name", "translations[it].name", "description");
	}

	@Test
	void everyParagraphIsWrittenInEveryLanguageAndOneOpensTheAffiliation() {
		var point = StoryPoint.ofChapter(CHAPTER);
		var later = new Paragraph(Map.of("it", "Dopo"), StoryWindow.from(StoryPoint.ofChapter(LATER)));
		var opened = Affiliation.builder()
			.romaji("Kaigun")
			.translations(Map.of("it", new AffiliationTranslation("Marina")))
			.firstAppearance(point)
			.kind(AffiliationKind.NAVY)
			.description(List.of(later));

		assertThat(opened.build().missingFields(List.of("it"))).containsExactly("description");
		var withOpening = List.of(discoveredAt(point, Map.of("it", "Forze")).getFirst(), later);
		assertThat(opened.description(withOpening).build().missingFields(List.of("it", "en")))
			.containsExactly("translations[en].name", "description[0].text[en]", "description[1].text[en]");
	}

	@Test
	void theImageIsNeverMissingAndAnEpisodeAloneIsAFirstAppearance() {
		var episode = StoryPoint.ofEpisode(CHAPTER);
		var complete = Affiliation.builder()
			.romaji("Kaigun")
			.translations(Map.of("it", new AffiliationTranslation("Marina")))
			.firstAppearance(episode)
			.kind(AffiliationKind.NAVY)
			.description(discoveredAt(episode, Map.of("it", "Forze")))
			.build();

		assertThat(complete.missingFields(List.of("it"))).isEmpty();
	}

	@Test
	void anotherImageOrFirstAppearanceOrParagraphMakesAnotherVersion() {
		var base = Affiliation.builder().romaji("Kaigun").build();

		assertThat(base).isEqualTo(base.toBuilder().firstAppearance(StoryPoint.NONE).description(List.of()).build())
			.isNotEqualTo(base.withImage("img"))
			.isNotEqualTo(base.toBuilder().firstAppearance(StoryPoint.ofChapter(CHAPTER)).build())
			.isNotEqualTo(base.toBuilder().description(paragraph("it", "Forze")).build());
	}

}
