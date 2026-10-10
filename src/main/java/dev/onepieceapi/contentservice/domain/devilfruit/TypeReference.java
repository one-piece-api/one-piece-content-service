package dev.onepieceapi.contentservice.domain.devilfruit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A Devil Fruit Type as another content points to it (implementation plan of the Devil
 * Fruit, D1, D4): its content id, and what to call it and its subcategories as it was
 * last approved - the type as of today, never a copy kept in the pointing version.
 *
 * @param names the name per language code, for the languages that have one
 * @param subcategories those a fruit of the type may name, in order
 */
public record TypeReference(UUID id, String romaji, Map<String, String> names,
		List<SubcategoryReference> subcategories) {

	public TypeReference {
		subcategories = subcategories == null ? List.of() : List.copyOf(subcategories);
	}

	/** A type without subcategories. */
	public TypeReference(UUID id, String romaji, Map<String, String> names) {
		this(id, romaji, names, List.of());
	}

	/** The subcategory with this id, when the type has it. */
	public Optional<SubcategoryReference> subcategory(UUID subcategoryId) {
		return this.subcategories.stream().filter(subcategory -> subcategory.id().equals(subcategoryId)).findFirst();
	}

}
