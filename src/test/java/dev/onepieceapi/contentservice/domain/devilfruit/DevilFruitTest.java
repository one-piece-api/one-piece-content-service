package dev.onepieceapi.contentservice.domain.devilfruit;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** How what an editor typed becomes what is stored and compared. */
class DevilFruitTest {

	private static final UUID PARAMECIA = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID LOGIA = UUID.fromString("5a1d9c2e-77b4-4d3a-8e0f-2c6b1a9d4e22");

	@Test
	void theSpaceAroundATextDoesNotCountAndTheTypeIsKept() {
		var typed = new DevilFruit("  Gomu Gomu ", PARAMECIA,
				Map.of("it", new DevilFruitTranslation(" Gom Gom ", "\tx\n", " Elastico ", "Mare\n")));

		var normalized = typed.normalized();

		assertThat(normalized.romaji()).isEqualTo("Gomu Gomu");
		assertThat(normalized.typeContentId()).isEqualTo(PARAMECIA);
		assertThat(normalized.translations())
			.containsExactly(entry("it", new DevilFruitTranslation("Gom Gom", "x", "Elastico", "Mare")));
	}

	@Test
	void aBlankTextIsAMissingOneAndALanguageNothingWasWrittenInIsLeftOut() {
		Map<String, DevilFruitTranslation> translations = new HashMap<>();
		translations.put("it", new DevilFruitTranslation("Gom", "  ", null, "\t"));
		translations.put("en", new DevilFruitTranslation("", "  ", null, null));

		var normalized = new DevilFruit("   ", null, translations).normalized();

		assertThat(normalized.romaji()).isNull();
		assertThat(normalized.translations()).containsOnlyKeys("it");
		assertThat(normalized.translationIn("it")).isEqualTo(new DevilFruitTranslation("Gom", null, null, null));
		assertThat(normalized.translationIn("en")).isEqualTo(DevilFruitTranslation.NONE);
	}

	@Test
	void theNamesAreOnlyThoseWritten() {
		var fruit = new DevilFruit("Gomu Gomu", PARAMECIA,
				Map.of("it", new DevilFruitTranslation("Gom Gom", null, null, null), "en",
						new DevilFruitTranslation(null, "Rubber", null, null)));

		assertThat(fruit.names()).containsExactly(entry("it", "Gom Gom"));
		assertThat(fruit.languages()).containsExactlyInAnyOrder("it", "en");
	}

	@Test
	void theTypeIsRequiredForReviewLikeTheRomajiAndBeforeTheTexts() {
		var empty = new DevilFruit(null, null, Map.of());

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("romaji", "type", "translations[en].name",
				"translations[en].description", "translations[en].advantages", "translations[en].disadvantages",
				"translations[it].name", "translations[it].description", "translations[it].advantages",
				"translations[it].disadvantages");
	}

	@Test
	void aCompleteFruitMissesNothing() {
		var complete = new DevilFruit("Gomu Gomu", PARAMECIA,
				Map.of("it", new DevilFruitTranslation("Gom", "Elastico", "Pro", "Contro")));

		assertThat(complete.missingFields(List.of("it"))).isEmpty();
	}

	@Test
	void twoFruitsDifferingOnlyByTheirTypeAreDifferent() {
		var translations = Map.of("it", new DevilFruitTranslation("Gom", "Elastico", "Pro", "Contro"));

		assertThat(new DevilFruit("Gomu", PARAMECIA, translations))
			.isEqualTo(new DevilFruit("Gomu", PARAMECIA, translations))
			.isNotEqualTo(new DevilFruit("Gomu", LOGIA, translations));
	}

}
