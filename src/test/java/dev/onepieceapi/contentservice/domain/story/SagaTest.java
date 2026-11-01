package dev.onepieceapi.contentservice.domain.story;

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
class SagaTest {

	private static final StoryPoint CHAPTER = StoryPoint.ofChapter(UUID.randomUUID());

	@Test
	void theSpaceAroundATextDoesNotCountAndTheNumberIsKept() {
		var typed = new Saga(3, Map.of("it", new TitleTranslation(" East Blue ")), paragraph("it", "\tIl mare\n"));

		var normalized = typed.normalized();

		assertThat(normalized.number()).isEqualTo(3);
		assertThat(normalized.translations()).containsExactly(entry("it", new TitleTranslation("East Blue")));
		assertThat(normalized.description()).containsExactly(new Paragraph(Map.of("it", "Il mare"), StoryWindow.NONE));
	}

	@Test
	void aBlankTextIsAMissingOneAndALanguageNothingWasWrittenInIsLeftOut() {
		Map<String, TitleTranslation> translations = new HashMap<>();
		translations.put("it", new TitleTranslation("East Blue"));
		translations.put("en", new TitleTranslation(" \t"));
		translations.put("fr", new TitleTranslation(null));

		var normalized = new Saga(null, translations, null).normalized();

		assertThat(normalized.translations()).containsOnlyKeys("it");
		assertThat(normalized.description()).isEmpty();
	}

	@Test
	void whatIsAlreadyTidyStaysAsItIs() {
		var tidy = new Saga(0, Map.of("en", new TitleTranslation("East Blue")), paragraph("en", "The sea"));

		assertThat(tidy.normalized()).isEqualTo(tidy);
	}

	@Test
	void theNamesAreTheTitlesWrittenAndTheNaturalKeyIsTheNumber() {
		var saga = new Saga(1, Map.of("it", new TitleTranslation("East Blue")), paragraph("en", "The sea"));

		assertThat(saga.names()).containsExactly(entry("it", "East Blue"));
		assertThat(saga.languages()).containsExactly("en", "it");
		assertThat(saga.nameField()).isEqualTo("title");
		assertThat(saga.naturalKey().field()).isEqualTo("number");
		assertThat(saga.naturalKey().value()).isEqualTo(1);
		assertThat(new Saga(null, Map.of(), null).naturalKey().isMissing()).isTrue();
	}

	@Test
	void theNumberIsRequiredForReviewBeforeTheTexts() {
		var empty = new Saga(null, Map.of(), null);

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("number", "translations[en].title",
				"translations[it].title", "description");
	}

	@Test
	void everyTitleAndParagraphOfEveryLanguageIsMissingUntilWrittenAndEachParagraphIsDiscovered() {
		var saga = new Saga(0, Map.of("en", new TitleTranslation("East Blue")), paragraph("it", "Il mare"));

		assertThat(saga.missingFields(List.of("en", "it"))).containsExactly("translations[it].title",
				"description[0].text[en]", "description[0].discovered");
	}

	@Test
	void aCompleteSagaMissesNothingItsParagraphDiscoveredAnywhere() {
		var complete = new Saga(0, Map.of("it", new TitleTranslation("East Blue")),
				discoveredAt(CHAPTER, Map.of("it", "Il mare")));

		assertThat(complete.missingFields(List.of("it"))).isEmpty();
		assertThat(complete.points()).containsExactly(CHAPTER);
	}

	@Test
	void twoSagasAreTheSameWhateverMapHoldsTheirTranslationsAndDifferByNumber() {
		Map<String, TitleTranslation> translations = new HashMap<>();
		translations.put("en", new TitleTranslation("East Blue"));

		var stored = new Saga(1, new TreeMap<>(translations), List.of());

		assertThat(new Saga(1, translations, null)).isEqualTo(stored);
		assertThat(new Saga(2, translations, null)).isNotEqualTo(stored);
	}

}
