package dev.onepieceapi.contentservice.domain.devilfruit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * A Devil Fruit Type as another content points to it (implementation plan of the Devil
 * Fruit, D1, D4): its content id, and what to call it and its subcategories as it was
 * last approved - the type as of today, never a copy kept in the pointing version.
 *
 * @param names the name per language code, for the languages that have one
 * @param subcategories those a fruit of the type may name, in order
 * @param dropped subcategories the type has since left out that fruits still name, as the
 * most recent approved version having them said: named, never offered
 */
public record TypeReference(UUID id, String romaji, Map<String, String> names, List<SubcategoryReference> subcategories,
		List<SubcategoryReference> dropped) {

	public TypeReference {
		subcategories = subcategories == null ? List.of() : List.copyOf(subcategories);
		dropped = dropped == null ? List.of() : List.copyOf(dropped);
	}

	/** A type as last approved, with nothing dropped. */
	public TypeReference(UUID id, String romaji, Map<String, String> names, List<SubcategoryReference> subcategories) {
		this(id, romaji, names, subcategories, List.of());
	}

	/** A type without subcategories. */
	public TypeReference(UUID id, String romaji, Map<String, String> names) {
		this(id, romaji, names, List.of());
	}

	/** The subcategory with this id, when the type has it or once had it. */
	public Optional<SubcategoryReference> subcategory(UUID subcategoryId) {
		return Stream.concat(this.subcategories.stream(), this.dropped.stream())
			.filter(subcategory -> subcategory.id().equals(subcategoryId))
			.findFirst();
	}

	/** The same type, naming one more subcategory it has since left out. */
	public TypeReference withDropped(SubcategoryReference subcategory) {
		return new TypeReference(this.id, this.romaji, this.names, this.subcategories,
				Stream.concat(this.dropped.stream(), Stream.of(subcategory)).toList());
	}

}
