package dev.onepieceapi.contentservice.domain.devilfruit;

import java.util.UUID;

/**
 * A Devil Fruit Type and one of its subcategories, as a fruit, its initial knowledge or
 * one of its reveals says them together (ADR-0010, T13).
 *
 * @param typeContentId the content of the type; null while not chosen
 * @param subcategoryId one of its subcategories; null for none
 * @param reveal the position of the reveal saying them; null for the others
 * @param initialKnowledge whether the initial knowledge says them
 */
public record DevilFruitClassification(UUID typeContentId, UUID subcategoryId, Integer reveal,
		boolean initialKnowledge) {

	/** Where it is in a request, e.g. {@code reveals[1].}; empty for the fruit itself. */
	public String prefix() {
		if (this.reveal != null) {
			return DevilFruit.REVEAL_PREFIX.formatted(this.reveal);
		}
		return this.initialKnowledge ? DevilFruit.INITIAL_KNOWLEDGE_PREFIX : "";
	}

	/** Whether the fruit itself says them, not a belief of the reader. */
	public boolean isTruth() {
		return this.reveal == null && !this.initialKnowledge;
	}

}
