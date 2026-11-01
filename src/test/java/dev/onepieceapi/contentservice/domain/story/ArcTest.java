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
class ArcTest {

	private static final UUID EAST_BLUE = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID ALABASTA = UUID.fromString("5a1d9c2e-77b4-4d3a-8e0f-2c6b1a9d4e22");

	private static final StoryPoint CHAPTER = StoryPoint.ofChapter(UUID.randomUUID());

	@Test
	void theSpaceAroundATextDoesNotCountAndTheNumberAndSagaAreKept() {
		var typed = new Arc(3, EAST_BLUE, Map.of("it", new TitleTranslation(" Romance Dawn ")),
				paragraph("it", "\tL'alba\n"));

		var normalized = typed.normalized();

		assertThat(normalized.number()).isEqualTo(3);
		assertThat(normalized.sagaContentId()).isEqualTo(EAST_BLUE);
		assertThat(normalized.translations()).containsExactly(entry("it", new TitleTranslation("Romance Dawn")));
		assertThat(normalized.description()).containsExactly(new Paragraph(Map.of("it", "L'alba"), StoryWindow.NONE));
	}

	@Test
	void aBlankTextIsAMissingOneAndALanguageNothingWasWrittenInIsLeftOut() {
		Map<String, TitleTranslation> translations = new HashMap<>();
		translations.put("it", new TitleTranslation("Romance Dawn"));
		translations.put("en", new TitleTranslation(" \t"));

		var normalized = new Arc(null, null, translations, null).normalized();

		assertThat(normalized.translations()).containsOnlyKeys("it");
	}

	@Test
	void theNamesAreTheTitlesWrittenAndTheNaturalKeyIsTheNumber() {
		var arc = new Arc(1, EAST_BLUE, Map.of("it", new TitleTranslation("Romance Dawn")),
				paragraph("en", "The dawn"));

		assertThat(arc.names()).containsExactly(entry("it", "Romance Dawn"));
		assertThat(arc.languages()).containsExactly("en", "it");
		assertThat(arc.nameField()).isEqualTo("title");
		assertThat(arc.naturalKey().field()).isEqualTo("number");
		assertThat(arc.naturalKey().value()).isEqualTo(1);
	}

	@Test
	void theNumberAndTheSagaAreRequiredForReviewBeforeTheTexts() {
		var empty = new Arc(null, null, Map.of(), null);

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("number", "saga", "translations[en].title",
				"translations[it].title", "description");
	}

	@Test
	void anArcMissesOnlyWhatIsNotWritten() {
		var titles = Map.of("it", new TitleTranslation("Romance Dawn"));
		var description = discoveredAt(CHAPTER, Map.of("it", "L'alba"));
		var withoutSaga = new Arc(0, null, titles, description);
		var withoutDescription = new Arc(0, EAST_BLUE, titles, List.of());

		assertThat(withoutSaga.missingFields(List.of("it"))).containsExactly("saga");
		assertThat(withoutDescription.missingFields(List.of("it"))).containsExactly("description");
	}

	@Test
	void aCompleteArcMissesNothing() {
		var complete = new Arc(0, EAST_BLUE, Map.of("it", new TitleTranslation("Romance Dawn")),
				discoveredAt(CHAPTER, Map.of("it", "L'alba")));

		assertThat(complete.missingFields(List.of("it"))).isEmpty();
	}

	@Test
	void twoArcsDifferingOnlyByTheirSagaAreDifferent() {
		Map<String, TitleTranslation> translations = new HashMap<>();
		translations.put("en", new TitleTranslation("Romance Dawn"));

		var stored = new Arc(1, EAST_BLUE, new TreeMap<>(translations), List.of());

		assertThat(new Arc(1, EAST_BLUE, translations, null)).isEqualTo(stored);
		assertThat(new Arc(1, ALABASTA, translations, null)).isNotEqualTo(stored);
		assertThat(new Arc(2, EAST_BLUE, translations, null)).isNotEqualTo(stored);
	}

}
