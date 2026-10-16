package dev.onepieceapi.contentservice.service.devilfruit;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitBelief;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitField;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReveal;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.exception.web.FieldViolation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.CHAPTER_100;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.CHAPTER_1044;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.INITIAL_KNOWLEDGE;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.NIKA;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.PARAMECIA;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.RUBBER_BODY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the initial knowledge and the reveals of a Devil Fruit must say on save
 * (implementation plan of the timeline, T13): values only for the fields a reveal names,
 * a value at every step but the last, an initial knowledge only for fields revealed, a
 * name never unknown, a subcategory with its type.
 */
class DevilFruitRevealRulesTest {

	private static final DevilFruitBelief RUBBER_DESCRIPTION = new DevilFruitBelief(null, null, null,
			Map.of("it", new DevilFruitTranslation(null, "Gomma", null, null)));

	private static DevilFruit fruit(DevilFruitBelief initialKnowledge, DevilFruitReveal... reveals) {
		return new DevilFruit("Hito Hito no Mi", GomuGomuReveals.ZOAN, null, Map.of(), null, StoryPoint.NONE,
				initialKnowledge, List.of(reveals));
	}

	private static DevilFruitReveal reveal(UUID chapter, Set<DevilFruitField> fields, DevilFruitBelief values) {
		return new DevilFruitReveal(new StoryPoint(chapter), fields, values, null);
	}

	@Test
	void theReferenceCaseIsValid() {
		assertThat(DevilFruitRevealRules.violations(GomuGomuReveals.fruit())).isEmpty();
	}

	@Test
	void aRevealHoldsAValueOnlyForAFieldItNames() {
		var named = reveal(CHAPTER_100, Set.of(DevilFruitField.ROMAJI), RUBBER_DESCRIPTION);

		assertThat(DevilFruitRevealRules.violations(fruit(DevilFruitBelief.NONE, named))).contains(new FieldViolation(
				"reveals[0].translations[it].description", "is not among the fields the reveal names"));
	}

	@Test
	void theLastRevealOfAFieldHoldsNoValue() {
		var last = reveal(CHAPTER_1044, Set.of(DevilFruitField.DESCRIPTION), RUBBER_DESCRIPTION);

		assertThat(DevilFruitRevealRules.violations(fruit(DevilFruitBelief.NONE, last)))
			.containsExactly(new FieldViolation("reveals[0].translations[it].description",
					"must be left out: the last reveal of a field reveals the truth"));
	}

	@Test
	void anEarlierRevealOfAFieldHoldsTheValueLearned() {
		var withoutValue = reveal(CHAPTER_100, Set.of(DevilFruitField.DESCRIPTION), null);

		assertThat(DevilFruitRevealRules.violations(fruit(INITIAL_KNOWLEDGE, withoutValue, NIKA)))
			.containsExactly(new FieldViolation("reveals[0].fields",
					"must give the value learned for description: a later reveal changes it again"));
	}

	@Test
	void theInitialKnowledgeHoldsOnlyFieldsARevealNames() {
		var romajiOnly = new DevilFruitBelief("Gomu Gomu no Mi", null, null, Map.of());

		assertThat(DevilFruitRevealRules.violations(fruit(romajiOnly)))
			.containsExactly(new FieldViolation("initialKnowledge.romaji", "must be left out: no reveal changes it"));
	}

	@Test
	void aFieldMayBeUnknownBeforeItsRevealButNotTheName() {
		var disadvantages = reveal(CHAPTER_1044, Set.of(DevilFruitField.DISADVANTAGES), null);
		var name = reveal(CHAPTER_1044, Set.of(DevilFruitField.NAME), null);

		assertThat(DevilFruitRevealRules.violations(fruit(DevilFruitBelief.NONE, disadvantages))).isEmpty();
		assertThat(DevilFruitRevealRules.violations(fruit(DevilFruitBelief.NONE, name)))
			.containsExactly(new FieldViolation("initialKnowledge",
					"must give the name known at the first appearance: a reveal changes it"));
		assertThat(DevilFruitRevealRules.violations(fruit(INITIAL_KNOWLEDGE, RUBBER_BODY, NIKA))).isEmpty();
	}

	@Test
	void aSubcategoryGoesWithItsType() {
		var subcategoryOnly = new DevilFruitBelief(null, null, UUID.randomUUID(), Map.of());
		var type = reveal(CHAPTER_1044, Set.of(DevilFruitField.TYPE), null);

		assertThat(DevilFruitRevealRules.violations(fruit(subcategoryOnly, type))).containsExactly(
				new FieldViolation("initialKnowledge.subcategory", "requires the Devil Fruit Type it belongs to"));
		assertThat(DevilFruitRevealRules.violations(fruit(new DevilFruitBelief(null, PARAMECIA, null, Map.of()), type)))
			.isEmpty();
	}

}
