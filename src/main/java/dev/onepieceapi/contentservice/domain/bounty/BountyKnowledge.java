package dev.onepieceapi.contentservice.domain.bounty;

import dev.onepieceapi.contentservice.domain.readertime.RevealedValues;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * What the reader holds of a Bounty that is not its current value
 * (docs/user-flows/content-editorial-workflow.md 3.7.1): whether it is inactive, known at
 * the first appearance or learned at a reveal.
 *
 * @param inactive null when not part of it
 */
public record BountyKnowledge(Boolean inactive) implements RevealedValues<BountyField, BountyKnowledge> {

	/** Nothing held. */
	public static final BountyKnowledge NONE = new BountyKnowledge(null);

	@Override
	public boolean holds(BountyField field) {
		return this.inactive != null;
	}

	@Override
	public boolean holdsKey(BountyField field) {
		return holds(field);
	}

	@Override
	public List<String> valueFields(BountyField field) {
		return holds(field) ? List.of(field.keyField()) : List.of();
	}

	@Override
	public List<String> missingTranslations(BountyField field, Collection<String> languages) {
		return List.of();
	}

	@Override
	public Set<String> languages() {
		return Set.of();
	}

	@Override
	public BountyKnowledge normalized() {
		return this;
	}

}
