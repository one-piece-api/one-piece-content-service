package dev.onepieceapi.contentservice.domain.devilfruittype;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** How what an editor typed becomes what is stored and compared. */
class DevilFruitTypeTest {

	@Test
	void theSpaceAroundATextDoesNotCount() {
		var typed = new DevilFruitType("  Shizen-kei ",
				Map.of("it", new DevilFruitTypeTranslation(" Rogia ", "\tx\n")));

		var normalized = typed.normalized();

		assertThat(normalized.romaji()).isEqualTo("Shizen-kei");
		assertThat(normalized.translations()).containsExactly(entry("it", new DevilFruitTypeTranslation("Rogia", "x")));
	}

	@Test
	void aBlankTextIsAMissingOne() {
		var typed = new DevilFruitType("   ", Map.of("it", new DevilFruitTypeTranslation("Rogia", "  ")));

		var normalized = typed.normalized();

		assertThat(normalized.romaji()).isNull();
		assertThat(normalized.translations().get("it")).isEqualTo(new DevilFruitTypeTranslation("Rogia", null));
	}

	@Test
	void aLanguageNothingWasWrittenInHasNoTranslation() {
		Map<String, DevilFruitTypeTranslation> translations = new HashMap<>();
		translations.put("it", new DevilFruitTypeTranslation(null, "Elementale"));
		translations.put("en", new DevilFruitTypeTranslation("", "  "));
		translations.put("fr", new DevilFruitTypeTranslation(null, null));

		var normalized = new DevilFruitType(null, translations).normalized();

		assertThat(normalized.translations()).containsOnlyKeys("it");
	}

	@Test
	void whatIsAlreadyTidyStaysAsItIs() {
		var tidy = new DevilFruitType("Zoan", Map.of("en", new DevilFruitTypeTranslation("Zoan", "Animal")));

		assertThat(tidy.normalized()).isEqualTo(tidy);
	}

}
