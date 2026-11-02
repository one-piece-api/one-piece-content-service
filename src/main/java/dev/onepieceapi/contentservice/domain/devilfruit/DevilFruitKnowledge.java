package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.readertime.RevealedValues;
import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the reader holds of a Devil Fruit that is not its current value
 * (docs/user-flows/content-editorial-workflow.md 3.7.1): known at the first appearance,
 * or learned at a reveal. A null value, or a language without a name, is not part of it.
 *
 * @param typeContentId the content of the Devil Fruit Type; null when not part of it
 * @param subcategoryId a subcategory of that type; null for none
 * @param names the name per language code
 */
public record DevilFruitKnowledge(String romaji, UUID typeContentId, UUID subcategoryId,
		Map<String, String> names) implements RevealedValues<DevilFruitField, DevilFruitKnowledge> {

	/** Nothing held. */
	public static final DevilFruitKnowledge NONE = new DevilFruitKnowledge(null, null, null, Map.of());

	private static final String NAME_FIELD = "translations[%s].name";

	public DevilFruitKnowledge {
		names = (names == null) ? Map.of() : Map.copyOf(names);
	}

	@Override
	public boolean holds(DevilFruitField field) {
		return switch (field) {
			case NAME -> !this.names.isEmpty();
			case ROMAJI -> this.romaji != null;
			case TYPE -> this.typeContentId != null || this.subcategoryId != null;
		};
	}

	@Override
	public boolean holdsKey(DevilFruitField field) {
		return (field == DevilFruitField.TYPE) ? this.typeContentId != null : holds(field);
	}

	@Override
	public List<String> valueFields(DevilFruitField field) {
		return switch (field) {
			case NAME -> this.names.keySet().stream().sorted().map(NAME_FIELD::formatted).toList();
			case ROMAJI -> (this.romaji == null) ? List.of() : List.of("romaji");
			case TYPE -> typeFields();
		};
	}

	@Override
	public List<String> missingTranslations(DevilFruitField field, Collection<String> languages) {
		if (field != DevilFruitField.NAME) {
			return List.of();
		}
		return languages.stream()
			.filter(language -> !this.names.containsKey(language))
			.map(NAME_FIELD::formatted)
			.toList();
	}

	@Override
	public Set<String> languages() {
		return this.names.keySet();
	}

	@Override
	public DevilFruitKnowledge normalized() {
		return new DevilFruitKnowledge(Text.stripToNull(this.romaji), this.typeContentId, this.subcategoryId,
				Text.written(this.names));
	}

	private List<String> typeFields() {
		List<String> fields = new ArrayList<>();
		if (this.typeContentId != null) {
			fields.add("type");
		}
		if (this.subcategoryId != null) {
			fields.add("subcategory");
		}
		return fields;
	}

}
