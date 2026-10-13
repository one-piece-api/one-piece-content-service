package dev.onepieceapi.contentservice.domain.story;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** How what an editor typed becomes what is stored and compared. */
class ArcTest {

	private static final UUID EAST_BLUE = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID ALABASTA = UUID.fromString("5a1d9c2e-77b4-4d3a-8e0f-2c6b1a9d4e22");

	@Test
	void theSpaceAroundATextDoesNotCountAndTheNumberAndSagaAreKept() {
		var typed = new Arc(3, EAST_BLUE, Map.of("it", new StoryTranslation(" Romance Dawn ", "\tL'alba\n")));

		var normalized = typed.normalized();

		assertThat(normalized.number()).isEqualTo(3);
		assertThat(normalized.sagaContentId()).isEqualTo(EAST_BLUE);
		assertThat(normalized.translations())
			.containsExactly(entry("it", new StoryTranslation("Romance Dawn", "L'alba")));
	}

	@Test
	void aBlankTextIsAMissingOneAndALanguageNothingWasWrittenInIsLeftOut() {
		Map<String, StoryTranslation> translations = new HashMap<>();
		translations.put("it", new StoryTranslation("  ", "L'alba"));
		translations.put("en", new StoryTranslation("", " \t"));

		var normalized = new Arc(null, null, translations).normalized();

		assertThat(normalized.translations()).containsOnlyKeys("it");
		assertThat(normalized.translations().get("it")).isEqualTo(new StoryTranslation(null, "L'alba"));
	}

	@Test
	void theNamesAreTheTitlesWrittenAndTheNaturalKeyIsTheNumber() {
		var arc = new Arc(1, EAST_BLUE,
				Map.of("it", new StoryTranslation("Romance Dawn", null), "en", new StoryTranslation(null, "The dawn")));

		assertThat(arc.names()).containsExactly(entry("it", "Romance Dawn"));
		assertThat(arc.languages()).containsExactlyInAnyOrder("it", "en");
		assertThat(arc.nameField()).isEqualTo("title");
		assertThat(arc.naturalKey().field()).isEqualTo("number");
		assertThat(arc.naturalKey().value()).isEqualTo(1);
	}

	@Test
	void theNumberAndTheSagaAreRequiredForReviewBeforeTheTexts() {
		var empty = new Arc(null, null, Map.of());

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("number", "saga", "translations[en].title",
				"translations[en].description", "translations[it].title", "translations[it].description");
	}

	@Test
	void anArcMissesOnlyWhatIsNotWritten() {
		var withoutSaga = new Arc(0, null, Map.of("it", new StoryTranslation("Romance Dawn", "L'alba")));
		var withoutDescription = new Arc(0, EAST_BLUE, Map.of("it", new StoryTranslation("Romance Dawn", null)));

		assertThat(withoutSaga.missingFields(List.of("it"))).containsExactly("saga");
		assertThat(withoutDescription.missingFields(List.of("it"))).containsExactly("translations[it].description");
	}

	@Test
	void aCompleteArcMissesNothing() {
		var complete = new Arc(0, EAST_BLUE, Map.of("it", new StoryTranslation("Romance Dawn", "L'alba")));

		assertThat(complete.missingFields(List.of("it"))).isEmpty();
	}

	@Test
	void twoArcsDifferingOnlyByTheirSagaAreDifferent() {
		Map<String, StoryTranslation> translations = new HashMap<>();
		translations.put("en", new StoryTranslation("Romance Dawn", "The dawn"));

		var stored = new Arc(1, EAST_BLUE, new TreeMap<>(translations));

		assertThat(new Arc(1, EAST_BLUE, translations)).isEqualTo(stored);
		assertThat(new Arc(1, ALABASTA, translations)).isNotEqualTo(stored);
		assertThat(new Arc(2, EAST_BLUE, translations)).isNotEqualTo(stored);
	}

}
