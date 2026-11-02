package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.readertime.NamedReveal;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.readertime.RevealableField;
import dev.onepieceapi.contentservice.domain.readertime.RevealedValues;
import dev.onepieceapi.contentservice.domain.readertime.Revealing;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

/**
 * What the reveals of an item must say on every save
 * (docs/user-flows/content-editorial-workflow.md 3.7.1), written once for whatever is
 * {@link Revealing}; their points are checked with every other point
 * ({@link ReaderAxisRules}), what submitting asks by the item itself:
 * <ul>
 * <li>each reveal has a point and names a field at least, and holds values only for the
 * fields it names;</li>
 * <li>the initial knowledge holds only fields some reveal names, and those the item is
 * never without (a name, a romaji) as soon as a reveal names them;</li>
 * <li>a value given holds what it cannot do without: a romaji for an identity, a type for
 * a subcategory.</li>
 * </ul>
 * Which values an entity's fields hold is the entity's to say; what they point to (a
 * type) its validator's.
 */
@UtilityClass
public class FieldRevealRules {

	private static final String REQUIRED = "is required";

	private static final String NO_FIELD = "must name at least one field";

	private static final String NOT_NAMED = "is not among the fields the reveal names";

	private static final String NEVER_REVEALED = "must be left out: no reveal names %s";

	private static final String ALWAYS_KNOWN = "must be known at the first appearance: a reveal names %s";

	private static final String KEY_MISSING = "is required with the other values of %s";

	/** Every violation at once, initial knowledge first, then the reveals in order. */
	public <F extends Enum<F> & RevealableField, V extends RevealedValues<F, V>> List<FieldViolation> violations(
			Revealing<F, V> item) {
		List<FieldViolation> violations = new ArrayList<>(initialKnowledgeViolations(item));
		List<NamedReveal> named = item.namedReveals();
		for (int index = 0; index < item.reveals().size(); index++) {
			violations.addAll(revealViolations(item, item.reveals().get(index), named.get(index)));
		}
		return violations;
	}

	private static <F extends Enum<F> & RevealableField> List<FieldViolation> initialKnowledgeViolations(
			Revealing<F, ?> item) {
		List<FieldViolation> violations = new ArrayList<>();
		String prefix = Revealing.INITIAL_KNOWLEDGE + ".";
		RevealedValues<F, ?> initial = item.initialKnowledge();
		for (F field : item.revealableFields()) {
			if (!item.revealed(field)) {
				initial.valueFields(field)
					.forEach(path -> violations
						.add(new FieldViolation(prefix + path, NEVER_REVEALED.formatted(field.fieldName()))));
			}
			else if (field.alwaysKnown() && !initial.holdsKey(field)) {
				violations
					.add(new FieldViolation(prefix + field.keyField(), ALWAYS_KNOWN.formatted(field.fieldName())));
			}
			else {
				keyMissing(prefix, initial, field).forEach(violations::add);
			}
		}
		return violations;
	}

	private static <F extends Enum<F> & RevealableField> List<FieldViolation> revealViolations(Revealing<F, ?> item,
			Reveal<F, ?> reveal, NamedReveal named) {
		List<FieldViolation> violations = new ArrayList<>();
		String prefix = named.field() + ".";
		if (reveal.revealedAt().isMissing()) {
			violations.add(new FieldViolation(named.pointField(), REQUIRED));
		}
		if (reveal.fields().isEmpty()) {
			violations.add(new FieldViolation(prefix + "fields", NO_FIELD));
		}
		for (F field : item.revealableFields()) {
			if (!reveal.names(field)) {
				reveal.values()
					.valueFields(field)
					.forEach(path -> violations.add(new FieldViolation(prefix + path, NOT_NAMED)));
			}
			else {
				keyMissing(prefix, reveal.values(), field).forEach(violations::add);
			}
		}
		return violations;
	}

	/** A value of the field held without the one it cannot do without. */
	private static <F extends Enum<F> & RevealableField> List<FieldViolation> keyMissing(String prefix,
			RevealedValues<F, ?> values, F field) {
		if (!values.holds(field) || values.holdsKey(field)) {
			return List.of();
		}
		return List.of(new FieldViolation(prefix + field.keyField(), KEY_MISSING.formatted(field.fieldName())));
	}

}
