package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuPhases.CHAPTER_1044;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuPhases.PARAMECIA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * The knowledge phases as part of a Devil Fruit's version (ADR-0010): how what an editor
 * typed in them is stored and compared, which languages they bring, and what the last one
 * owes at submission.
 */
class DevilFruitPhaseTest {

	private static DevilFruit withPhases(DevilFruitPhase... phases) {
		return new DevilFruit("Hito Hito no Mi", GomuGomuPhases.ZOAN, null,
				Map.of("it", GomuGomuPhases.TRUTH_IT, "en", GomuGomuPhases.TRUTH_EN), null, StoryPoint.NONE,
				List.of(phases));
	}

	@Test
	void aPhaseIsNormalizedLikeTheFruitAndKeepsItsChapterAndType() {
		Map<String, DevilFruitTranslation> typed = new HashMap<>();
		typed.put("it", new DevilFruitTranslation(" Gom Gom ", "  ", null, null));
		typed.put("en", new DevilFruitTranslation("", null, null, " "));
		var phase = new DevilFruitPhase(new StoryPoint(CHAPTER_1044), "  Gomu Gomu ", PARAMECIA, null, typed);

		var normalized = withPhases(phase).normalized().phases().getFirst();

		assertThat(normalized.romaji()).isEqualTo("Gomu Gomu");
		assertThat(normalized.supersededAt()).isEqualTo(new StoryPoint(CHAPTER_1044));
		assertThat(normalized.typeContentId()).isEqualTo(PARAMECIA);
		assertThat(normalized.translations())
			.containsExactly(entry("it", new DevilFruitTranslation("Gom Gom", null, null, null)));
	}

	@Test
	void aPhaseOfBlanksOnlyDefinesNothing() {
		var blank = new DevilFruitPhase(new StoryPoint(CHAPTER_1044), " ", null, null,
				Map.of("it", new DevilFruitTranslation(" ", null, null, null)));

		assertThat(withPhases(blank).normalized().phases().getFirst().definesNothing()).isTrue();
		assertThat(new DevilFruitPhase(null, null, null, UUID.randomUUID(), null).definesNothing()).isFalse();
		assertThat(GomuGomuPhases.FIRST_BELIEF.definesNothing()).isFalse();
	}

	@Test
	void nothingChosenIsNoneAndNoListIsNoPhase() {
		var phase = new DevilFruitPhase(null, "Gomu", null, null, null);
		var fruit = new DevilFruit("Gomu", null, null, Map.of(), null, null, null);

		assertThat(phase.supersededAt()).isEqualTo(StoryPoint.NONE);
		assertThat(phase.translations()).isEmpty();
		assertThat(fruit.phases()).isEmpty();
	}

	@Test
	void theLanguagesAreThoseOfTheFruitAndOfItsPhases() {
		var phase = new DevilFruitPhase(new StoryPoint(CHAPTER_1044), null, null, null,
				Map.of("fr", new DevilFruitTranslation("Fruit du Caoutchouc", null, null, null)));

		assertThat(withPhases(phase).languages()).containsExactly("en", "fr", "it");
		assertThat(withPhases(phase).names()).containsOnlyKeys("it", "en");
	}

	@Test
	void theLastPhaseMustDescribeTheFruitInEveryLanguageForReview() {
		var withoutDescription = new DevilFruitPhase(new StoryPoint(CHAPTER_1044), "Gomu Gomu no Mi", null, null,
				Map.of("it", new DevilFruitTranslation(null, "Gomma", null, null)));

		assertThat(withPhases(GomuGomuPhases.GOMU_GOMU, withoutDescription).missingFields(List.of("en", "it")))
			.containsExactly("phases[1].translations[en].description");
		assertThat(withPhases(withoutDescription, GomuGomuPhases.GOMU_GOMU).missingFields(List.of("en", "it")))
			.isEmpty();
	}

	@Test
	void twoVersionsDifferingOnlyByTheirPhasesAreDifferent() {
		var renamed = new DevilFruitPhase(new StoryPoint(CHAPTER_1044), "Gomu Gomu", PARAMECIA, null,
				GomuGomuPhases.GOMU_GOMU.translations());

		assertThat(withPhases(GomuGomuPhases.GOMU_GOMU)).isNotEqualTo(withPhases(renamed))
			.isNotEqualTo(withPhases())
			.isEqualTo(withPhases(GomuGomuPhases.GOMU_GOMU));
	}

	@Test
	void anotherImageKeepsThePhases() {
		assertThat(withPhases(GomuGomuPhases.GOMU_GOMU).withImage("image").phases())
			.containsExactly(GomuGomuPhases.GOMU_GOMU);
	}

}
