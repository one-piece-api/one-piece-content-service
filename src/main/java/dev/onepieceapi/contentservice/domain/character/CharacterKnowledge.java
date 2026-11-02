package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.readertime.RevealedValues;
import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the reader holds of a Character that is not its current value
 * (docs/user-flows/content-editorial-workflow.md 3.7.1): known at the first appearance,
 * or learned at a reveal. The identity is the romaji, the Japanese name and the name per
 * language, revealed together; the romaji is what it cannot do without. A null value, or
 * a language without a name, is not part of it.
 *
 * @param names the name per language code
 */
public record CharacterKnowledge(String romaji, String japaneseName, CharacterStatus status,
		Map<String, String> names) implements RevealedValues<CharacterField, CharacterKnowledge> {

	/** Nothing held. */
	public static final CharacterKnowledge NONE = new CharacterKnowledge(null, null, null, Map.of());

	private static final String NAME_FIELD = "translations[%s].name";

	public CharacterKnowledge {
		names = (names == null) ? Map.of() : Map.copyOf(names);
	}

	@Override
	public boolean holds(CharacterField field) {
		return switch (field) {
			case IDENTITY -> this.romaji != null || this.japaneseName != null || !this.names.isEmpty();
			case STATUS -> this.status != null;
		};
	}

	@Override
	public boolean holdsKey(CharacterField field) {
		return (field == CharacterField.IDENTITY) ? this.romaji != null : holds(field);
	}

	@Override
	public List<String> valueFields(CharacterField field) {
		List<String> fields = new ArrayList<>();
		if (field == CharacterField.STATUS) {
			addIf(fields, this.status != null, "status");
			return fields;
		}
		addIf(fields, this.romaji != null, "romaji");
		addIf(fields, this.japaneseName != null, "japaneseName");
		this.names.keySet().stream().sorted().map(NAME_FIELD::formatted).forEach(fields::add);
		return fields;
	}

	@Override
	public List<String> missingTranslations(CharacterField field, Collection<String> languages) {
		if (field != CharacterField.IDENTITY) {
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
	public CharacterKnowledge normalized() {
		return new CharacterKnowledge(Text.stripToNull(this.romaji), Text.stripToNull(this.japaneseName), this.status,
				Text.written(this.names));
	}

	private static void addIf(List<String> fields, boolean held, String field) {
		if (held) {
			fields.add(field);
		}
	}

}
