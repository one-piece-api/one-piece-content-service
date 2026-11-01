package dev.onepieceapi.contentservice.domain.devilfruittype;

import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.readertime.ParagraphFixtures.discoveredAt;
import static dev.onepieceapi.contentservice.domain.readertime.ParagraphFixtures.paragraph;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** How what an editor typed becomes what is stored and compared. */
class DevilFruitTypeTest {

	@Test
	void theSpaceAroundATextDoesNotCount() {
		var typed = DevilFruitType.builder()
			.romaji("  Shizen-kei ")
			.translations(Map.of("it", new DevilFruitTypeTranslation(" Rogia ")))
			.description(paragraph("it", "\tx\n"))
			.advantages(paragraph(Map.of("it", " Intangibile ", "en", " ")))
			.build();

		var normalized = typed.normalized();

		assertThat(normalized.romaji()).isEqualTo("Shizen-kei");
		assertThat(normalized.translations()).containsExactly(entry("it", new DevilFruitTypeTranslation("Rogia")));
		assertThat(normalized.description()).containsExactly(new Paragraph(Map.of("it", "x"), StoryWindow.NONE));
		assertThat(normalized.advantages())
			.containsExactly(new Paragraph(Map.of("it", "Intangibile"), StoryWindow.NONE));
	}

	@Test
	void aLanguageNothingWasWrittenInHasNoTranslation() {
		Map<String, DevilFruitTypeTranslation> translations = new HashMap<>();
		translations.put("it", new DevilFruitTypeTranslation("Rogia"));
		translations.put("en", new DevilFruitTypeTranslation("  "));
		translations.put("fr", new DevilFruitTypeTranslation(null));

		var normalized = DevilFruitType.builder().translations(translations).build().normalized();

		assertThat(normalized.romaji()).isNull();
		assertThat(normalized.translations()).containsOnlyKeys("it");
	}

	@Test
	void whatIsAlreadyTidyStaysAsItIs() {
		var tidy = DevilFruitType.builder()
			.romaji("Zoan")
			.translations(Map.of("en", new DevilFruitTypeTranslation("Zoan")))
			.description(paragraph("en", "Animal"))
			.build();

		assertThat(tidy.normalized()).isEqualTo(tidy);
	}

	@Test
	void aLanguageWithoutTranslationSaysNothingInIt() {
		var english = new DevilFruitTypeTranslation("Zoan");
		var body = DevilFruitType.builder().romaji("Zoan").translations(Map.of("en", english)).build();

		assertThat(body.translationIn("en")).isEqualTo(english);
		assertThat(body.translationIn("it")).isEqualTo(new DevilFruitTypeTranslation(null));
	}

	@Test
	void twoBodiesAreTheSameWhateverMapHoldsTheirTranslations() {
		Map<String, DevilFruitTypeTranslation> translations = new HashMap<>();
		translations.put("en", new DevilFruitTypeTranslation("Zoan"));

		var stored = DevilFruitType.builder().romaji("Zoan").translations(new TreeMap<>(translations)).build();

		assertThat(DevilFruitType.builder().romaji("Zoan").translations(translations).build()).isEqualTo(stored);
		assertThat(stored.toBuilder().romaji("ZOAN").build()).isNotEqualTo(stored);
	}

	@Test
	void whatIsRequiredForReviewIsNamedInOrderEverySectionLast() {
		var empty = DevilFruitType.builder().build();

		assertThat(empty.missingFields(List.of("en"))).containsExactly("romaji", "firstAppearance",
				"translations[en].name", "description", "advantages", "disadvantages");
	}

	@Test
	void aTypeWithEverySectionOpenedAtItsFirstAppearanceMissesNothing() {
		var chapter = StoryPoint.ofChapter(UUID.randomUUID());
		var type = DevilFruitType.builder()
			.romaji("Zoan")
			.translations(Map.of("it", new DevilFruitTypeTranslation("Zoo Zoo")))
			.firstAppearance(chapter)
			.description(discoveredAt(chapter, Map.of("it", "Animale")))
			.advantages(discoveredAt(chapter, Map.of("it", "Forza")))
			.disadvantages(discoveredAt(chapter, Map.of("it", "Mare")))
			.build();

		assertThat(type.missingFields(List.of("it"))).isEmpty();
	}

	@Test
	void theTextsOfASubcategoryAreTidiedAndALanguageWithNothingInItDropped() {
		var typed = DevilFruitType.builder()
			.romaji("Zoan")
			.subcategories(List.of(new DevilFruitTypeSubcategory(null,
					Map.of("it", new DevilFruitTypeSubcategoryTranslation(" Antico ", " "), "en",
							new DevilFruitTypeSubcategoryTranslation(" ", null)))))
			.build();

		var normalized = typed.normalized();

		assertThat(normalized.subcategories()).containsExactly(new DevilFruitTypeSubcategory(null,
				Map.of("it", new DevilFruitTypeSubcategoryTranslation("Antico", null))));
	}

	@Test
	void everySubcategoryJustAddedGetsAnIdAndTheOthersKeepTheirs() {
		UUID ancient = UUID.randomUUID();
		var typed = DevilFruitType.builder()
			.romaji("Zoan")
			.subcategories(List.of(new DevilFruitTypeSubcategory(ancient, Map.of()),
					new DevilFruitTypeSubcategory(null, Map.of())))
			.build();

		var identified = typed.identified();

		assertThat(identified.subcategories().getFirst().id()).isEqualTo(ancient);
		assertThat(identified.subcategories().get(1).id()).isNotNull().isNotEqualTo(ancient);
		assertThat(typed.subcategoryIds()).containsExactly(ancient);
		assertThat(identified.subcategory(ancient)).isPresent();
	}

	@Test
	void aSubcategoryMissesItsNameAndDescriptionInEveryLanguageAndCountsAmongTheLanguagesUsed() {
		var english = new DevilFruitTypeSubcategoryTranslation("Ancient", null);
		var body = DevilFruitType.builder()
			.romaji("Zoan")
			.subcategories(List.of(new DevilFruitTypeSubcategory(UUID.randomUUID(), Map.of("en", english))))
			.build();

		assertThat(body.missingFields(List.of("en", "it")))
			.contains("subcategories[0].translations[en].description", "subcategories[0].translations[it].name",
					"subcategories[0].translations[it].description")
			.doesNotContain("subcategories[0].translations[en].name");
		assertThat(body.languages()).containsExactly("en");
		assertThat(body.names()).isEmpty();
	}

	@Test
	void aTypeWithoutSubcategoriesHasAnEmptyListAndOrderMakesTwoBodiesDifferent() {
		var ancient = new DevilFruitTypeSubcategory(UUID.randomUUID(), Map.of());
		var mythical = new DevilFruitTypeSubcategory(UUID.randomUUID(), Map.of());
		var zoan = DevilFruitType.builder().romaji("Zoan");

		assertThat(zoan.build().subcategories()).isEmpty();
		assertThat(zoan.subcategories(List.of(ancient, mythical)).build())
			.isNotEqualTo(zoan.subcategories(List.of(mythical, ancient)).build());
	}

	@Test
	void thePointsShownAreItsOwnThenThoseOfItsSubcategoriesThatHaveOne() {
		var ofType = StoryPoint.ofChapter(UUID.randomUUID());
		var ofSubcategory = StoryPoint.ofChapter(UUID.randomUUID());
		var ofParagraph = StoryPoint.ofEpisode(UUID.randomUUID());
		var ancient = new DevilFruitTypeSubcategory(UUID.randomUUID(), Map.of(), ofSubcategory);
		var plain = new DevilFruitTypeSubcategory(UUID.randomUUID(), Map.of());

		var type = DevilFruitType.builder()
			.romaji("Zoan")
			.subcategories(List.of(ancient, plain))
			.firstAppearance(ofType)
			.advantages(discoveredAt(ofParagraph, Map.of("it", "Forza")))
			.build();

		assertThat(type.shownPoints()).containsExactly(ofType, ofParagraph, ofSubcategory);
		assertThat(DevilFruitType.builder().romaji("Zoan").build().shownPoints()).isEmpty();
	}

	@Test
	void normalizingKeepsTheFirstAppearancesOfTheTypeAndOfItsSubcategories() {
		var chapter = StoryPoint.ofChapter(UUID.randomUUID());
		var subcategory = new DevilFruitTypeSubcategory(UUID.randomUUID(),
				Map.of("it", new DevilFruitTypeSubcategoryTranslation(" Antico ", "Descrizione")), chapter);
		var type = DevilFruitType.builder()
			.romaji(" Zoan ")
			.subcategories(List.of(subcategory))
			.firstAppearance(chapter)
			.build();

		var normalized = type.normalized();

		assertThat(normalized.firstAppearance()).isEqualTo(chapter);
		assertThat(normalized.subcategories().getFirst().firstAppearance()).isEqualTo(chapter);
		assertThat(DevilFruitType.builder().romaji("Zoan").build().lacksFirstAppearance()).isTrue();
	}

}
