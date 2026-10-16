package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.CHAPTER_1;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.CHAPTER_1044;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.PARAMECIA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * The reveals as part of a Devil Fruit's version (ADR-0010): how what an editor typed in
 * them is stored and compared, which languages they bring, and what the last one owes at
 * submission.
 */
class DevilFruitRevealTest {

	private static DevilFruit withReveals(DevilFruitReveal... reveals) {
		return new DevilFruit("Hito Hito no Mi", GomuGomuReveals.ZOAN, null,
				Map.of("it", GomuGomuReveals.TRUTH_IT, "en", GomuGomuReveals.TRUTH_EN), null, new StoryPoint(CHAPTER_1),
				List.of(reveals));
	}

	@Test
	void aRevealIsNormalizedLikeTheFruitAndKeepsItsChapterAndType() {
		Map<String, DevilFruitTranslation> typed = new HashMap<>();
		typed.put("it", new DevilFruitTranslation(" Gom Gom ", "  ", null, null));
		typed.put("en", new DevilFruitTranslation("", null, null, " "));
		var reveal = new DevilFruitReveal(new StoryPoint(CHAPTER_1044), "  Gomu Gomu ", PARAMECIA, null, typed,
				new HashMap<>(Map.of("it", " Il frutto è Nika ", "en", "  ")));

		var normalized = withReveals(reveal).normalized().reveals().getFirst();

		assertThat(normalized.romaji()).isEqualTo("Gomu Gomu");
		assertThat(normalized.revealedAt()).isEqualTo(new StoryPoint(CHAPTER_1044));
		assertThat(normalized.typeContentId()).isEqualTo(PARAMECIA);
		assertThat(normalized.translations())
			.containsExactly(entry("it", new DevilFruitTranslation("Gom Gom", null, null, null)));
		assertThat(normalized.titles()).containsExactly(entry("it", "Il frutto è Nika"));
	}

	@Test
	void aRevealOfBlanksOnlyDefinesNothing() {
		var blank = new DevilFruitReveal(new StoryPoint(CHAPTER_1044), " ", null, null,
				Map.of("it", new DevilFruitTranslation(" ", null, null, null)));

		assertThat(withReveals(blank).normalized().reveals().getFirst().definesNothing()).isTrue();
		var titled = new DevilFruitReveal(new StoryPoint(CHAPTER_1044), null, null, null, Map.of(),
				Map.of("it", "Il frutto è Nika"));

		assertThat(titled.definesNothing()).isTrue();
		assertThat(new DevilFruitReveal(null, null, null, UUID.randomUUID(), null).definesNothing()).isFalse();
		assertThat(GomuGomuReveals.FIRST_BELIEF.definesNothing()).isFalse();
	}

	@Test
	void nothingChosenIsNoneAndNoListIsNoReveal() {
		var reveal = new DevilFruitReveal(null, "Gomu", null, null, null);
		var fruit = new DevilFruit("Gomu", null, null, Map.of(), null, null, null);

		assertThat(reveal.revealedAt()).isEqualTo(StoryPoint.NONE);
		assertThat(reveal.translations()).isEmpty();
		assertThat(fruit.reveals()).isEmpty();
	}

	@Test
	void theLanguagesAreThoseOfTheFruitAndOfItsReveals() {
		var reveal = new DevilFruitReveal(new StoryPoint(CHAPTER_1044), null, null, null,
				Map.of("fr", new DevilFruitTranslation("Fruit du Caoutchouc", null, null, null)));

		assertThat(withReveals(reveal).languages()).containsExactly("en", "fr", "it");
		var titled = new DevilFruitReveal(new StoryPoint(CHAPTER_1044), null, null, null, Map.of(),
				Map.of("de", "Titel"));
		assertThat(withReveals(titled).languages()).containsExactly("de", "en", "it");
		assertThat(withReveals(reveal).names()).containsOnlyKeys("it", "en");
	}

	@Test
	void theLastRevealMustDescribeTheFruitInEveryLanguageForReview() {
		var withoutDescription = new DevilFruitReveal(new StoryPoint(CHAPTER_1044), "Gomu Gomu no Mi", null, null,
				Map.of("it", new DevilFruitTranslation(null, "Gomma", null, null)),
				Map.of("it", "Titolo", "en", "Title"));

		assertThat(withReveals(GomuGomuReveals.GOMU_GOMU, withoutDescription).missingFields(List.of("en", "it")))
			.containsExactly("reveals[1].translations[en].description");
		assertThat(withReveals(withoutDescription, GomuGomuReveals.GOMU_GOMU).missingFields(List.of("en", "it")))
			.isEmpty();
	}

	@Test
	void twoVersionsDifferingOnlyByTheirRevealsAreDifferent() {
		var renamed = new DevilFruitReveal(new StoryPoint(CHAPTER_1044), "Gomu Gomu", PARAMECIA, null,
				GomuGomuReveals.GOMU_GOMU.translations());

		assertThat(withReveals(GomuGomuReveals.GOMU_GOMU)).isNotEqualTo(withReveals(renamed))
			.isNotEqualTo(withReveals())
			.isEqualTo(withReveals(GomuGomuReveals.GOMU_GOMU));
	}

	@Test
	void everyRevealMustTitleItsRevealInEveryLanguageForReviewButNotOnSave() {
		var untitled = new DevilFruitReveal(new StoryPoint(CHAPTER_1044), null, null, null,
				GomuGomuReveals.GOMU_GOMU.translations(), Map.of("it", "Il frutto è Nika"));

		assertThat(withReveals(GomuGomuReveals.FIRST_BELIEF, untitled).missingFields(List.of("en", "it")))
			.containsExactly("reveals[1].titles[en]");
		assertThat(
				withReveals(GomuGomuReveals.FIRST_BELIEF, GomuGomuReveals.GOMU_GOMU).missingFields(List.of("en", "it")))
			.isEmpty();
	}

	@Test
	void aTitleChangedMakesTwoVersionsDifferent() {
		var retitled = new DevilFruitReveal(new StoryPoint(CHAPTER_1044), GomuGomuReveals.GOMU_GOMU.romaji(), PARAMECIA,
				null, GomuGomuReveals.GOMU_GOMU.translations(), Map.of("it", "Altro", "en", "Other"));

		assertThat(withReveals(GomuGomuReveals.GOMU_GOMU)).isNotEqualTo(withReveals(retitled));
	}

	@Test
	void anotherImageKeepsTheReveals() {
		assertThat(withReveals(GomuGomuReveals.GOMU_GOMU).withImage("image").reveals())
			.containsExactly(GomuGomuReveals.GOMU_GOMU);
	}

}
