package dev.onepieceapi.contentservice.service.devilfruit;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitBelief;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitField;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReveal;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * What the reveals of a Devil Fruit must say on every save (implementation plan of the
 * timeline, T13; ADR-0010), on top of the generic {@code RevealRules}:
 * <ul>
 * <li>a reveal holds a value only for a field it names;</li>
 * <li>the last reveal of a field holds no value - it reveals the truth - and every
 * earlier one holds the value learned there;</li>
 * <li>the initial knowledge holds only fields a reveal names, and the name whenever a
 * reveal names it: a fruit is never nameless;</li>
 * <li>a subcategory goes with the type it belongs to.</li>
 * </ul>
 * Whether a value is written in every language is asked at submission only.
 */
@UtilityClass
public class DevilFruitRevealRules {

	private static final String FIELDS = "fields";

	private static final String SUBCATEGORY = "subcategory";

	private static final String TYPE = "type";

	private static final String ROMAJI = "romaji";

	private static final String TRANSLATED = "translations[%s].%s";

	private static final String NOT_NAMED = "is not among the fields the reveal names";

	private static final String LAST_HOLDS_VALUE = "must be left out: the last reveal of a field reveals the truth";

	private static final String VALUE_MISSING = "must give the value learned for %s: a later reveal changes it again";

	private static final String NEVER_REVEALED = "must be left out: no reveal changes it";

	private static final String NAME_UNKNOWN = "must give the name known at the first appearance: a reveal changes it";

	private static final String SUBCATEGORY_WITHOUT_TYPE = "requires the Devil Fruit Type it belongs to";

	/** Every violation at once. */
	public List<FieldViolation> violations(DevilFruit fruit) {
		List<FieldViolation> violations = new ArrayList<>();
		for (int index = 0; index < fruit.reveals().size(); index++) {
			DevilFruitReveal reveal = fruit.reveals().get(index);
			String prefix = DevilFruit.REVEAL_PREFIX.formatted(index);
			Stream.of(DevilFruitField.values())
				.filter(field -> !reveal.reveals(field))
				.forEach(field -> violations.addAll(valuePaths(prefix, reveal.values(), field, NOT_NAMED)));
		}
		for (DevilFruitField field : DevilFruitField.values()) {
			violations.addAll(stepsOf(fruit, field));
		}
		if (!fruit.revealsOf(DevilFruitField.NAME).isEmpty() && !fruit.initialKnowledge().holds(DevilFruitField.NAME)) {
			violations.add(new FieldViolation(initialKnowledgePath(), NAME_UNKNOWN));
		}
		subcategoryWithoutType(DevilFruit.INITIAL_KNOWLEDGE_PREFIX, fruit.initialKnowledge())
			.ifPresent(violations::add);
		for (int index = 0; index < fruit.reveals().size(); index++) {
			subcategoryWithoutType(DevilFruit.REVEAL_PREFIX.formatted(index), fruit.reveals().get(index).values())
				.ifPresent(violations::add);
		}
		return violations;
	}

	/**
	 * The steps of one field: the initial knowledge only when a reveal names it, a value
	 * at each reveal but the last, none at the last.
	 */
	private static List<FieldViolation> stepsOf(DevilFruit fruit, DevilFruitField field) {
		List<FieldViolation> violations = new ArrayList<>();
		List<Integer> positions = fruit.revealsOf(field);
		if (positions.isEmpty()) {
			violations.addAll(
					valuePaths(DevilFruit.INITIAL_KNOWLEDGE_PREFIX, fruit.initialKnowledge(), field, NEVER_REVEALED));
			return violations;
		}
		int last = positions.get(positions.size() - 1);
		for (int position : positions) {
			DevilFruitBelief values = fruit.reveals().get(position).values();
			String prefix = DevilFruit.REVEAL_PREFIX.formatted(position);
			if (position == last) {
				violations.addAll(valuePaths(prefix, values, field, LAST_HOLDS_VALUE));
			}
			else if (!values.holds(field)) {
				violations.add(new FieldViolation(prefix + FIELDS, VALUE_MISSING.formatted(field.fieldName())));
			}
		}
		return violations;
	}

	/**
	 * One violation per place the belief holds the field: per language when translated.
	 */
	private static List<FieldViolation> valuePaths(String prefix, DevilFruitBelief belief, DevilFruitField field,
			String message) {
		if (!belief.holds(field)) {
			return List.of();
		}
		return switch (field) {
			case ROMAJI -> List.of(new FieldViolation(prefix + ROMAJI, message));
			case TYPE ->
				List.of(new FieldViolation(prefix + (belief.typeContentId() == null ? SUBCATEGORY : TYPE), message));
			default -> belief.languages()
				.stream()
				.filter(language -> belief.holds(field, language))
				.map(language -> new FieldViolation(prefix + TRANSLATED.formatted(language, field.fieldName()),
						message))
				.toList();
		};
	}

	private static Optional<FieldViolation> subcategoryWithoutType(String prefix, DevilFruitBelief belief) {
		return belief.subcategoryId() != null && belief.typeContentId() == null
				? Optional.of(new FieldViolation(prefix + SUBCATEGORY, SUBCATEGORY_WITHOUT_TYPE)) : Optional.empty();
	}

	private static String initialKnowledgePath() {
		return DevilFruit.INITIAL_KNOWLEDGE_PREFIX.substring(0, DevilFruit.INITIAL_KNOWLEDGE_PREFIX.length() - 1);
	}

}
