package dev.onepieceapi.contentservice.domain.devilfruittype;

import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What one version of a Devil Fruit Type says
 * (docs/user-flows/content-editorial-workflow.md 3.1.1): one romaji shared by every
 * language, a name, description, advantages and disadvantages per language code, and its
 * subcategories in the order they are shown. Plugged into the generic {@code Content} and
 * {@code Version}, which carry the workflow around it.
 *
 * @param subcategories none for a type without them; never null
 */
public record DevilFruitType(String romaji, Map<String, DevilFruitTypeTranslation> translations,
		List<DevilFruitTypeSubcategory> subcategories) implements ContentBody<DevilFruitType> {

	public static final int ROMAJI_MAX_LENGTH = 100;

	private static final String ROMAJI_FIELD = "romaji";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String NAME_FIELD = "translations[%s].name";

	private static final String DESCRIPTION_FIELD = "translations[%s].description";

	private static final String ADVANTAGES_FIELD = "translations[%s].advantages";

	private static final String DISADVANTAGES_FIELD = "translations[%s].disadvantages";

	private static final String SUBCATEGORY_NAME_FIELD = "subcategories[%d].translations[%s].name";

	private static final String SUBCATEGORY_DESCRIPTION_FIELD = "subcategories[%d].translations[%s].description";

	public DevilFruitType {
		subcategories = subcategories == null ? List.of() : List.copyOf(subcategories);
	}

	/** A type without subcategories. */
	public DevilFruitType(String romaji, Map<String, DevilFruitTypeTranslation> translations) {
		this(romaji, translations, List.of());
	}

	@Override
	public DevilFruitType normalized() {
		Map<String, DevilFruitTypeTranslation> written = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			DevilFruitTypeTranslation normalized = translation.normalized();
			if (!normalized.isEmpty()) {
				written.put(language, normalized);
			}
		});
		return new DevilFruitType(Text.stripToNull(this.romaji), written,
				this.subcategories.stream().map(DevilFruitTypeSubcategory::normalized).toList());
	}

	/** Every subcategory just added gets its id. */
	@Override
	public DevilFruitType identified() {
		return new DevilFruitType(this.romaji, this.translations,
				this.subcategories.stream().map(DevilFruitTypeSubcategory::identified).toList());
	}

	/** The languages of the type and of its subcategories. */
	@Override
	public Set<String> languages() {
		Set<String> languages = new TreeSet<>(this.translations.keySet());
		this.subcategories.forEach(subcategory -> languages.addAll(subcategory.translations().keySet()));
		return languages;
	}

	@Override
	public Map<String, String> names() {
		Map<String, String> names = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			if (translation.name() != null) {
				names.put(language, translation.name());
			}
		});
		return names;
	}

	/**
	 * The romaji, then a name, a description, advantages and disadvantages per language,
	 * then a name and a description per language of each subcategory - every one missing.
	 */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.romaji == null) {
			missing.add(ROMAJI_FIELD);
		}
		for (String language : languages) {
			DevilFruitTypeTranslation translation = translationIn(language);
			if (translation.name() == null) {
				missing.add(NAME_FIELD.formatted(language));
			}
			if (translation.description() == null) {
				missing.add(DESCRIPTION_FIELD.formatted(language));
			}
			if (translation.advantages() == null) {
				missing.add(ADVANTAGES_FIELD.formatted(language));
			}
			if (translation.disadvantages() == null) {
				missing.add(DISADVANTAGES_FIELD.formatted(language));
			}
		}
		for (int index = 0; index < this.subcategories.size(); index++) {
			DevilFruitTypeSubcategory subcategory = this.subcategories.get(index);
			for (String language : languages) {
				DevilFruitTypeSubcategoryTranslation translation = subcategory.translationIn(language);
				if (translation.name() == null) {
					missing.add(SUBCATEGORY_NAME_FIELD.formatted(index, language));
				}
				if (translation.description() == null) {
					missing.add(SUBCATEGORY_DESCRIPTION_FIELD.formatted(index, language));
				}
			}
		}
		return missing;
	}

	/**
	 * What it says in this language - nothing at all, when no translation was written.
	 */
	public DevilFruitTypeTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, DevilFruitTypeTranslation.NONE);
	}

	/** The ids of its subcategories; those just added, without one yet, are left out. */
	public Set<UUID> subcategoryIds() {
		return this.subcategories.stream()
			.map(DevilFruitTypeSubcategory::id)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
	}

	/** The subcategory with this id, when the type has it. */
	public Optional<DevilFruitTypeSubcategory> subcategory(UUID id) {
		return this.subcategories.stream().filter(subcategory -> id.equals(subcategory.id())).findFirst();
	}

}
