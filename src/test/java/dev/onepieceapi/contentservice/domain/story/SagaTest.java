package dev.onepieceapi.contentservice.domain.story;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** How what an editor typed becomes what is stored and compared. */
class SagaTest {

	@Test
	void theSpaceAroundATextDoesNotCountAndTheNumberIsKept() {
		var typed = new Saga(3, Map.of("it", new StoryTranslation(" East Blue ", "\tIl mare\n")));

		var normalized = typed.normalized();

		assertThat(normalized.number()).isEqualTo(3);
		assertThat(normalized.translations())
			.containsExactly(entry("it", new StoryTranslation("East Blue", "Il mare")));
	}

	@Test
	void aBlankTextIsAMissingOneAndALanguageNothingWasWrittenInIsLeftOut() {
		Map<String, StoryTranslation> translations = new HashMap<>();
		translations.put("it", new StoryTranslation("East Blue", "  "));
		translations.put("en", new StoryTranslation("", " \t"));
		translations.put("fr", new StoryTranslation(null, null));

		var normalized = new Saga(null, translations).normalized();

		assertThat(normalized.translations()).containsOnlyKeys("it");
		assertThat(normalized.translations().get("it")).isEqualTo(new StoryTranslation("East Blue", null));
	}

	@Test
	void whatIsAlreadyTidyStaysAsItIs() {
		var tidy = new Saga(0, Map.of("en", new StoryTranslation("East Blue", "The sea")));

		assertThat(tidy.normalized()).isEqualTo(tidy);
	}

	@Test
	void theNamesAreTheTitlesWrittenAndTheNaturalKeyIsTheNumber() {
		var saga = new Saga(1,
				Map.of("it", new StoryTranslation("East Blue", null), "en", new StoryTranslation(null, "The sea")));

		assertThat(saga.names()).containsExactly(entry("it", "East Blue"));
		assertThat(saga.languages()).containsExactlyInAnyOrder("it", "en");
		assertThat(saga.nameField()).isEqualTo("title");
		assertThat(saga.naturalKey().field()).isEqualTo("number");
		assertThat(saga.naturalKey().value()).isEqualTo(1);
		assertThat(new Saga(null, Map.of()).naturalKey().isMissing()).isTrue();
	}

	@Test
	void theNumberIsRequiredForReviewBeforeTheTexts() {
		var empty = new Saga(null, Map.of());

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("number", "translations[en].title",
				"translations[en].description", "translations[it].title", "translations[it].description");
	}

	@Test
	void everyTitleAndDescriptionOfEveryLanguageIsMissingUntilWritten() {
		var saga = new Saga(0,
				Map.of("en", new StoryTranslation("East Blue", null), "it", new StoryTranslation(null, "Il mare")));

		assertThat(saga.missingFields(List.of("en", "it"))).containsExactly("translations[en].description",
				"translations[it].title");
	}

	@Test
	void aCompleteSagaMissesNothing() {
		var complete = new Saga(0, Map.of("it", new StoryTranslation("East Blue", "Il mare")));

		assertThat(complete.missingFields(List.of("it"))).isEmpty();
	}

	@Test
	void twoSagasAreTheSameWhateverMapHoldsTheirTranslationsAndDifferByNumber() {
		Map<String, StoryTranslation> translations = new HashMap<>();
		translations.put("en", new StoryTranslation("East Blue", "The sea"));

		var stored = new Saga(1, new TreeMap<>(translations));

		assertThat(new Saga(1, translations)).isEqualTo(stored);
		assertThat(new Saga(2, translations)).isNotEqualTo(stored);
	}

}
