package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.readertime.KnowledgePhases;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuPhases.CHAPTER_100;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuPhases.CHAPTER_1044;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuPhases.MYTHICAL;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuPhases.NUMBERS;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuPhases.PARAMECIA;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuPhases.ZOAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * How a Devil Fruit reads at a chapter (implementation plan of the timeline, T9;
 * ADR-0010): each field from the first phase still believed there that defines it,
 * otherwise the truth - on the shared {@link GomuGomuPhases} case.
 */
class DevilFruitPhaseResolutionTest {

	private static DevilFruit at(int chapter) {
		return GomuGomuPhases.fruit().asBelieved(KnowledgePhases.believedAt(chapter, NUMBERS::get));
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 50, 99 })
	void beforeTheFirstRevealEachFieldComesFromTheFirstPhaseDefiningIt(int chapter) {
		var believed = at(chapter);

		assertThat(believed.translationIn("en").description()).isEqualTo("Rubber, nothing more");
		assertThat(believed.translationIn("en").name()).isEqualTo("Gum-Gum Fruit");
		assertThat(believed.romaji()).isEqualTo("Gomu Gomu no Mi");
		assertThat(believed.typeContentId()).isEqualTo(PARAMECIA);
		assertThat(believed.subcategoryId()).isNull();
	}

	@ParameterizedTest
	@ValueSource(ints = { 100, 500, 1043 })
	void aFieldChangingTwiceReadsTheBeliefOfTheNextReveal(int chapter) {
		var believed = at(chapter);

		assertThat(believed.translationIn("it").description()).isEqualTo("Un corpo di gomma");
		assertThat(believed.translationIn("it").name()).isEqualTo("Frutto Gom Gom");
		assertThat(believed.typeContentId()).isEqualTo(PARAMECIA);
	}

	@ParameterizedTest
	@ValueSource(ints = { 1044, 1100 })
	void fromTheLastRevealOnEverythingIsTheTruth(int chapter) {
		var believed = at(chapter);

		assertThat(believed.romaji()).isEqualTo("Hito Hito no Mi, Model: Nika");
		assertThat(believed.typeContentId()).isEqualTo(ZOAN);
		assertThat(believed.subcategoryId()).isEqualTo(MYTHICAL);
		assertThat(believed.translations()).isEqualTo(GomuGomuPhases.fruit().translations());
		assertThat(believed.phases()).isEmpty();
	}

	@Test
	void aFieldNoPhaseDefinesIsAlwaysTheTruth() {
		assertThat(at(1).translationIn("en").advantages()).isEqualTo("Frees its eater");
		assertThat(at(1).firstAppearance()).isEqualTo(GomuGomuPhases.fruit().firstAppearance());
	}

	@Test
	void aPhaseWhoseChapterHasNoKnownNumberIsNeverBelieved() {
		var believed = GomuGomuPhases.fruit().asBelieved(KnowledgePhases.believedAt(1, Map.of(CHAPTER_100, 100)::get));

		assertThat(believed.typeContentId()).isEqualTo(ZOAN);
		assertThat(believed.translationIn("en").description()).isEqualTo("Rubber, nothing more");
	}

	@Test
	void aPhaseDefiningOnlyTheSubcategoryKeepsTheTypeInForceAfterIt() {
		UUID ancient = UUID.randomUUID();
		var onlySubcategory = new DevilFruitPhase(new StoryPoint(CHAPTER_100), null, null, ancient, Map.of());
		var fruit = new DevilFruit("Hito Hito no Mi, Model: Nika", ZOAN, MYTHICAL, Map.of(), null, StoryPoint.NONE,
				List.of(onlySubcategory));

		var believed = fruit.asBelieved(KnowledgePhases.believedAt(1, NUMBERS::get));

		assertThat(believed.typeContentId()).isEqualTo(ZOAN);
		assertThat(believed.subcategoryId()).isEqualTo(ancient);
		assertThat(fruit.typeInForceAt(0)).isEqualTo(ZOAN);
	}

	@Test
	void theTypeInForceAtAPhaseIsItsOwnThenThatOfALaterPhaseThenTheTruth() {
		var onlySubcategory = new DevilFruitPhase(new StoryPoint(CHAPTER_100), null, null, UUID.randomUUID(), Map.of());
		var fruit = new DevilFruit("Nika", ZOAN, MYTHICAL, Map.of(), null, StoryPoint.NONE,
				List.of(onlySubcategory, GomuGomuPhases.GOMU_GOMU));

		assertThat(fruit.typeInForceAt(0)).isEqualTo(PARAMECIA);
		assertThat(fruit.typeInForceAt(1)).isEqualTo(PARAMECIA);
		assertThat(fruit.classifications())
			.extracting(DevilFruitClassification::typeContentId, DevilFruitClassification::phase)
			.containsExactly(tuple(ZOAN, null), tuple(PARAMECIA, 0), tuple(PARAMECIA, 1));
	}

	@Test
	void theChaptersAreThoseOfTheFirstAppearanceThenOfThePhases() {
		assertThat(GomuGomuPhases.fruit().chapterIds()).containsExactly(GomuGomuPhases.CHAPTER_1, CHAPTER_100,
				CHAPTER_1044);
		assertThat(GomuGomuPhases.fruit().phaseTypeIds()).containsExactly(PARAMECIA);
	}

}
