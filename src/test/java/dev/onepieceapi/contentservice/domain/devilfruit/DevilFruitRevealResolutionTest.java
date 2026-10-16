package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.readertime.Reveals;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.CHAPTER_100;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.CHAPTER_1044;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.MYTHICAL;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.NUMBERS;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.PARAMECIA;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.ZOAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * How a Devil Fruit reads at a chapter (implementation plan of the timeline, T13;
 * ADR-0010): a field no reveal names is the truth; otherwise the value of the last reveal
 * reached naming it - the truth at the last - else the initial knowledge, unknown when
 * that holds none. On the shared {@link GomuGomuReveals} case.
 */
class DevilFruitRevealResolutionTest {

	private static DevilFruit at(int chapter) {
		return GomuGomuReveals.fruit().knownAt(Reveals.reachedAt(chapter, NUMBERS::get));
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 50, 99 })
	void beforeAnyRevealTheFieldsAreTheInitialKnowledge(int chapter) {
		var known = at(chapter);

		assertThat(known.translationIn("en").description()).isEqualTo("Rubber, nothing more");
		assertThat(known.translationIn("en").name()).isEqualTo("Gum-Gum Fruit");
		assertThat(known.romaji()).isEqualTo("Gomu Gomu no Mi");
		assertThat(known.typeContentId()).isEqualTo(PARAMECIA);
		assertThat(known.subcategoryId()).isNull();
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 100, 1043 })
	void aFieldTheInitialKnowledgeLeavesOutIsUnknownUntilRevealed(int chapter) {
		assertThat(at(chapter).translationIn("it").disadvantages()).isNull();
	}

	@ParameterizedTest
	@ValueSource(ints = { 100, 500, 1043 })
	void anIntermediateRevealGivesTheValueLearnedThere(int chapter) {
		var known = at(chapter);

		assertThat(known.translationIn("it").description()).isEqualTo("Un corpo di gomma");
		assertThat(known.translationIn("it").name()).isEqualTo("Frutto Gom Gom");
		assertThat(known.typeContentId()).isEqualTo(PARAMECIA);
	}

	@ParameterizedTest
	@ValueSource(ints = { 1044, 1100 })
	void fromTheLastRevealOnEverythingIsTheTruth(int chapter) {
		var known = at(chapter);

		assertThat(known.romaji()).isEqualTo("Hito Hito no Mi, Model: Nika");
		assertThat(known.typeContentId()).isEqualTo(ZOAN);
		assertThat(known.subcategoryId()).isEqualTo(MYTHICAL);
		assertThat(known.translations()).isEqualTo(GomuGomuReveals.fruit().translations());
		assertThat(known.reveals()).isEmpty();
		assertThat(known.initialKnowledge()).isEqualTo(DevilFruitBelief.NONE);
	}

	@Test
	void aFieldNoRevealNamesIsAlwaysTheTruth() {
		assertThat(at(1).translationIn("en").advantages()).isEqualTo("Frees its eater");
		assertThat(at(1).firstAppearance()).isEqualTo(GomuGomuReveals.fruit().firstAppearance());
	}

	@Test
	void aRevealWhoseChapterHasNoKnownNumberCountsAsReached() {
		var known = GomuGomuReveals.fruit().knownAt(Reveals.reachedAt(1, Map.of(CHAPTER_100, 100)::get));

		assertThat(known.typeContentId()).isEqualTo(ZOAN);
		assertThat(known.translationIn("en").description()).isEqualTo("The fruit of the sun god");
	}

	@Test
	void theTypeAndTheSubcategoryAreLearnedTogether() {
		UUID ancient = UUID.randomUUID();
		var initial = new DevilFruitBelief(null, ZOAN, ancient, Map.of());
		var nika = new DevilFruitReveal(new StoryPoint(CHAPTER_1044), Set.of(DevilFruitField.TYPE), null, null);
		var fruit = new DevilFruit("Nika", ZOAN, MYTHICAL, Map.of(), null, StoryPoint.NONE, initial, List.of(nika));

		assertThat(fruit.knownAt(Reveals.reachedAt(1, NUMBERS::get)).subcategoryId()).isEqualTo(ancient);
		assertThat(fruit.knownAt(Reveals.reachedAt(1044, NUMBERS::get)).subcategoryId()).isEqualTo(MYTHICAL);
		assertThat(fruit.classifications())
			.extracting(DevilFruitClassification::typeContentId, DevilFruitClassification::subcategoryId,
					DevilFruitClassification::prefix)
			.containsExactly(tuple(ZOAN, MYTHICAL, ""), tuple(ZOAN, ancient, "initialKnowledge."));
	}

	@Test
	void theClassificationsAreThoseOfTheTruthTheInitialKnowledgeAndTheIntermediateReveals() {
		var paramecia = new DevilFruitBelief(null, PARAMECIA, null, Map.of());
		var logia = UUID.randomUUID();
		var step = new DevilFruitReveal(new StoryPoint(CHAPTER_100), Set.of(DevilFruitField.TYPE),
				new DevilFruitBelief(null, logia, null, Map.of()), null);
		var fruit = new DevilFruit("Nika", ZOAN, MYTHICAL, Map.of(), null, StoryPoint.NONE, paramecia,
				List.of(step, GomuGomuReveals.NIKA));

		assertThat(fruit.classifications())
			.extracting(DevilFruitClassification::typeContentId, DevilFruitClassification::prefix)
			.containsExactly(tuple(ZOAN, ""), tuple(PARAMECIA, "initialKnowledge."), tuple(logia, "reveals[0]."));
		assertThat(fruit.beliefTypeIds()).containsExactly(PARAMECIA, logia);
		assertThat(fruit.knownAt(Reveals.reachedAt(500, NUMBERS::get)).typeContentId()).isEqualTo(logia);
	}

	@Test
	void theChaptersAreThoseOfTheFirstAppearanceThenOfTheReveals() {
		assertThat(GomuGomuReveals.fruit().chapterIds()).containsExactly(GomuGomuReveals.CHAPTER_1, CHAPTER_100,
				CHAPTER_1044);
		assertThat(GomuGomuReveals.fruit().beliefTypeIds()).containsExactly(PARAMECIA);
		assertThat(GomuGomuReveals.fruit().revealsOf(DevilFruitField.DESCRIPTION)).containsExactly(0, 1);
	}

}
