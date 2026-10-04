package dev.onepieceapi.contentservice.domain.devilfruittype;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** How what an editor typed becomes what is stored and compared. */
class DevilFruitTypeTest {

	@Test
	void theSpaceAroundATextDoesNotCount() {
		var typed = new DevilFruitType("  Shizen-kei ",
				Map.of("it", new DevilFruitTypeTranslation(" Rogia ", "\tx\n", " Intangibile ", "Mare\n")));

		var normalized = typed.normalized();

		assertThat(normalized.romaji()).isEqualTo("Shizen-kei");
		assertThat(normalized.translations())
			.containsExactly(entry("it", new DevilFruitTypeTranslation("Rogia", "x", "Intangibile", "Mare")));
	}

	@Test
	void aBlankTextIsAMissingOne() {
		var typed = new DevilFruitType("   ", Map.of("it", new DevilFruitTypeTranslation("Rogia", "  ", " ", "\t")));

		var normalized = typed.normalized();

		assertThat(normalized.romaji()).isNull();
		assertThat(normalized.translations().get("it"))
			.isEqualTo(new DevilFruitTypeTranslation("Rogia", null, null, null));
	}

	@Test
	void aLanguageNothingWasWrittenInHasNoTranslation() {
		Map<String, DevilFruitTypeTranslation> translations = new HashMap<>();
		translations.put("it", new DevilFruitTypeTranslation(null, "Elementale", null, null));
		translations.put("en", new DevilFruitTypeTranslation("", "  ", null, null));
		translations.put("fr", new DevilFruitTypeTranslation(null, null, null, null));
		translations.put("es", new DevilFruitTypeTranslation(null, null, null, "Agua de mar"));

		var normalized = new DevilFruitType(null, translations).normalized();

		assertThat(normalized.translations()).containsOnlyKeys("it", "es");
	}

	@Test
	void whatIsAlreadyTidyStaysAsItIs() {
		var tidy = new DevilFruitType("Zoan",
				Map.of("en", new DevilFruitTypeTranslation("Zoan", "Animal", null, null)));

		assertThat(tidy.normalized()).isEqualTo(tidy);
	}

	@Test
	void aLanguageWithoutTranslationSaysNothingInIt() {
		var english = new DevilFruitTypeTranslation("Zoan", "Animal", null, null);
		var body = new DevilFruitType("Zoan", Map.of("en", english));

		assertThat(body.translationIn("en")).isEqualTo(english);
		assertThat(body.translationIn("it")).isEqualTo(new DevilFruitTypeTranslation(null, null, null, null));
	}

	@Test
	void twoBodiesAreTheSameWhateverMapHoldsTheirTranslations() {
		Map<String, DevilFruitTypeTranslation> translations = new HashMap<>();
		translations.put("en", new DevilFruitTypeTranslation("Zoan", "Animal", null, null));

		var stored = new DevilFruitType("Zoan", new TreeMap<>(translations));

		assertThat(new DevilFruitType("Zoan", translations)).isEqualTo(stored);
		assertThat(new DevilFruitType("ZOAN", translations)).isNotEqualTo(stored);
	}

}
