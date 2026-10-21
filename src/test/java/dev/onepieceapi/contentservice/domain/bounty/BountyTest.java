package dev.onepieceapi.contentservice.domain.bounty;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * How what an editor typed becomes what is stored and compared, and what a bounty needs.
 */
class BountyTest {

	private static final UUID PREVIOUS = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID HOLDER = UUID.fromString("7b3e6d1c-2f48-4a90-b5c7-9d0e1f2a3b44");

	@Test
	void theKeyIsThePostersNameAndTheAmountInPlainDigitsNamingBothFields() {
		var key = new Bounty("Monkey D. Luffy", 3_000_000_000L, BountyCondition.DEAD_OR_ALIVE, false, null, null,
				Map.of(), null, StoryPoint.NONE)
			.naturalKey();

		assertThat(key.text()).isEqualTo("Monkey D. Luffy 3000000000");
		assertThat(key.field()).isEqualTo("key");
		assertThat(key.fields()).containsExactly("posterName", "amount");
	}

	@ParameterizedTest
	@CsvSource(value = { "NULL,100", "Luffy,NULL", "NULL,NULL" }, nullValues = "NULL")
	void theKeyIsMissingWhileEitherPartIs(String posterName, Long amount) {
		var bounty = new Bounty(posterName, amount, null, false, null, null, Map.of(), null, StoryPoint.NONE);

		assertThat(bounty.naturalKey().isMissing()).isTrue();
		assertThat(bounty.naturalKeyText()).isNull();
	}

	@Test
	void anAmountOfZeroIsAnAmount() {
		var bounty = new Bounty("Nobody", 0L, null, false, null, null, Map.of(), null, StoryPoint.NONE);

		assertThat(bounty.naturalKeyText()).isEqualTo("Nobody 0");
	}

	@Test
	void aBountyHasNoNameAndItsPostersNameMayRepeat() {
		var bounty = new Bounty("Luffy", 1L, null, false, null, null, Map.of("it", new BountyTranslation("Taglia")),
				null, StoryPoint.NONE);

		assertThat(bounty.names()).isEmpty();
		assertThat(bounty.uniqueNames()).isFalse();
		assertThat(bounty.languages()).containsExactly("it");
	}

	@Test
	void theSpaceAroundTheNameDoesNotCountAndABlankOneIsMissing() {
		var typed = new Bounty("  Roronoa Zoro ", 320_000_000L, BountyCondition.ONLY_ALIVE, true, PREVIOUS, HOLDER,
				Map.of("it", new BountyTranslation("  Pirata\n")), "img", StoryPoint.ofChapter(PREVIOUS));

		var normalized = typed.normalized();

		assertThat(normalized.posterName()).isEqualTo("Roronoa Zoro");
		assertThat(normalized.translations()).containsExactly(entry("it", new BountyTranslation("Pirata")));
		assertThat(normalized.inactive()).isTrue();
		assertThat(normalized.previousContentId()).isEqualTo(PREVIOUS);
		assertThat(normalized.holderContentId()).isEqualTo(HOLDER);
		assertThat(normalized.imageId()).isEqualTo("img");
		assertThat(normalized.firstAppearance()).isEqualTo(StoryPoint.ofChapter(PREVIOUS));
		assertThat(new Bounty("   ", 1L, null, false, null, null, Map.of(), null, null).normalized().posterName())
			.isNull();
	}

	@Test
	void aLanguageNothingWasWrittenInIsLeftOut() {
		Map<String, BountyTranslation> translations = new HashMap<>();
		translations.put("it", new BountyTranslation("Taglia"));
		translations.put("en", new BountyTranslation("  "));

		var normalized = new Bounty("Luffy", 1L, null, false, null, null, translations, null, null).normalized();

		assertThat(normalized.translations()).containsOnlyKeys("it");
		assertThat(normalized.translationIn("en")).isEqualTo(BountyTranslation.NONE);
	}

	@Test
	void whatIsRequiredForReviewIsNamedInOrderBeforeTheDescriptions() {
		var empty = new Bounty(null, null, null, false, null, null, Map.of(), null, null);

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("posterName", "amount", "condition",
				"firstAppearance", "translations[en].description", "translations[it].description");
	}

	@Test
	void thePreviousTheHolderTheFlagAndThePosterAreNeverMissing() {
		var complete = new Bounty("Luffy", 1_500_000_000L, BountyCondition.DEAD_OR_ALIVE, false, null, null,
				Map.of("it", new BountyTranslation("Taglia")), null, StoryPoint.ofChapter(PREVIOUS));

		assertThat(complete.missingFields(List.of("it"))).isEmpty();
	}

	@Test
	void anotherPosterOrFlagOrPreviousOrHolderMakesAnotherVersion() {
		var base = new Bounty("Luffy", 1L, BountyCondition.DEAD_OR_ALIVE, false, null, null, Map.of(), null, null);

		assertThat(base)
			.isEqualTo(new Bounty("Luffy", 1L, BountyCondition.DEAD_OR_ALIVE, false, null, null, Map.of(), null,
					StoryPoint.NONE))
			.isNotEqualTo(base.withImage("img"))
			.isNotEqualTo(
					new Bounty("Luffy", 1L, BountyCondition.DEAD_OR_ALIVE, true, null, null, Map.of(), null, null))
			.isNotEqualTo(
					new Bounty("Luffy", 1L, BountyCondition.DEAD_OR_ALIVE, false, PREVIOUS, null, Map.of(), null, null))
			.isNotEqualTo(
					new Bounty("Luffy", 1L, BountyCondition.DEAD_OR_ALIVE, false, null, HOLDER, Map.of(), null, null));
	}

}
