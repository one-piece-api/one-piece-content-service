package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitField;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitFixtures;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitKnowledge;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.exception.web.FieldViolation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * What the reveals must say on every save (workflow 3.7.1), written once and shown here
 * on a Devil Fruit.
 */
class FieldRevealRulesTest {

	private static final StoryPoint FIRST_APPEARANCE = StoryPoint.ofChapter(UUID.randomUUID());

	private static final StoryPoint LATER = StoryPoint.ofChapter(UUID.randomUUID());

	private static final UUID PARAMECIA = UUID.randomUUID();

	private static final Map<String, String> TITLES = Map.of("it", "Rivelazione", "en", "Reveal");

	@Test
	void aFruitWithoutRevealsAndWithNothingKnownAtFirstIsValid() {
		assertThat(FieldRevealRules.violations(fruit(DevilFruitKnowledge.NONE))).isEmpty();
	}

	@Test
	void aRevealOfTheCurrentValuesWithTheInitialOnesIsValid() {
		var initial = new DevilFruitKnowledge("Gomu Gomu no Mi", PARAMECIA, null, Map.of("en", "Gum-Gum Fruit"));

		assertThat(FieldRevealRules.violations(fruit(initial, reveal(LATER, Set.of(DevilFruitField.values())))))
			.isEmpty();
	}

	@Test
	void aRevealNeedsAPointAndAFieldAndHoldsValuesOnlyOfTheFieldsItNames() {
		var noPoint = reveal(StoryPoint.NONE, Set.of(DevilFruitField.TYPE));
		var noField = reveal(LATER, Set.of());
		var notNamed = new Reveal<>(LATER, Set.of(DevilFruitField.TYPE),
				new DevilFruitKnowledge("Mera Mera no Mi", PARAMECIA, null, Map.of()), TITLES);

		var violations = FieldRevealRules.violations(fruit(DevilFruitKnowledge.NONE, noPoint, noField, notNamed));

		assertThat(violations).extracting(FieldViolation::field, FieldViolation::message)
			.containsExactly(tuple("reveals[0].revealedAt", "is required"),
					tuple("reveals[1].fields", "must name at least one field"),
					tuple("reveals[2].romaji", "is not among the fields the reveal names"));
	}

	@Test
	void theInitialKnowledgeHoldsOnlyFieldsSomeRevealNamesAndThoseNeverMissingAsSoonAsOneIs() {
		var initial = new DevilFruitKnowledge(null, PARAMECIA, null, Map.of("en", "Gum-Gum Fruit"));

		var violations = FieldRevealRules
			.violations(fruit(initial, reveal(LATER, Set.of(DevilFruitField.NAME, DevilFruitField.ROMAJI))));

		assertThat(violations).extracting(FieldViolation::field, FieldViolation::message)
			.containsExactly(
					tuple("initialKnowledge.romaji", "must be known at the first appearance: a reveal names romaji"),
					tuple("initialKnowledge.type", "must be left out: no reveal names type"));
	}

	@Test
	void aSubcategoryIsNeverHeldWithoutItsType() {
		var initial = new DevilFruitKnowledge(null, null, UUID.randomUUID(), Map.of());
		var reveal = new Reveal<>(LATER, Set.of(DevilFruitField.TYPE),
				new DevilFruitKnowledge(null, null, UUID.randomUUID(), Map.of()), TITLES);

		var violations = FieldRevealRules.violations(fruit(initial, reveal));

		assertThat(violations).extracting(FieldViolation::field, FieldViolation::message)
			.containsExactly(tuple("initialKnowledge.type", "is required with the other values of type"),
					tuple("reveals[0].type", "is required with the other values of type"));
	}

	@SafeVarargs
	private static DevilFruit fruit(DevilFruitKnowledge initial,
			Reveal<DevilFruitField, DevilFruitKnowledge>... reveals) {
		return DevilFruitFixtures.complete("Hito Hito no Mi, Model: Nika", UUID.randomUUID(), null, FIRST_APPEARANCE)
			.toBuilder()
			.initialKnowledge(initial)
			.reveals(List.of(reveals))
			.build();
	}

	private static Reveal<DevilFruitField, DevilFruitKnowledge> reveal(StoryPoint point, Set<DevilFruitField> fields) {
		return new Reveal<>(point, fields, DevilFruitKnowledge.NONE, TITLES);
	}

}
