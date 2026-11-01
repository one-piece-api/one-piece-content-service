package dev.onepieceapi.contentservice.domain.devilfruittype;

import dev.onepieceapi.contentservice.domain.readertime.FirstAppearing;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.domain.readertime.Paragraphed;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
import dev.onepieceapi.contentservice.domain.workflow.Text;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What one version of a Devil Fruit Type says
 * (docs/user-flows/content-editorial-workflow.md 3.1.1): one romaji shared by every
 * language, a name per language code, its description, advantages and disadvantages as
 * paragraphs (3.7.3), and its subcategories in the order they are shown. Plugged into the
 * generic {@code Content} and {@code Version}, which carry the workflow around it.
 *
 * @param subcategories none for a type without them; never null
 * @param firstAppearance the chapter the reader first meets it in (implementation plan of
 * the timeline, T7); {@link StoryPoint#NONE} for none, which is complete - it is required
 * only to go online
 * @param description its paragraphs, in order; none is never null
 * @param advantages as {@code description}
 * @param disadvantages as {@code description}
 */
@Builder(toBuilder = true)
public record DevilFruitType(String romaji, Map<String, DevilFruitTypeTranslation> translations,
		List<DevilFruitTypeSubcategory> subcategories, StoryPoint firstAppearance, List<Paragraph> description,
		List<Paragraph> advantages,
		List<Paragraph> disadvantages) implements ContentBody<DevilFruitType>, FirstAppearing, Paragraphed {

	public static final int ROMAJI_MAX_LENGTH = 100;

	private static final String ROMAJI_FIELD = "romaji";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String NAME_FIELD = "translations[%s].name";

	private static final String SUBCATEGORY_NAME_FIELD = "subcategories[%d].translations[%s].name";

	private static final String SUBCATEGORY_DESCRIPTION_FIELD = "subcategories[%d].translations[%s].description";

	/** Nothing chosen is {@link StoryPoint#NONE}, not null; no list is null. */
	public DevilFruitType {
		translations = translations == null ? Map.of() : translations;
		subcategories = subcategories == null ? List.of() : List.copyOf(subcategories);
		firstAppearance = StoryPoint.orNone(firstAppearance);
		description = description == null ? List.of() : List.copyOf(description);
		advantages = advantages == null ? List.of() : List.copyOf(advantages);
		disadvantages = disadvantages == null ? List.of() : List.copyOf(disadvantages);
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
		return toBuilder().romaji(Text.stripToNull(this.romaji))
			.translations(written)
			.subcategories(this.subcategories.stream().map(DevilFruitTypeSubcategory::normalized).toList())
			.description(Paragraph.normalized(this.description))
			.advantages(Paragraph.normalized(this.advantages))
			.disadvantages(Paragraph.normalized(this.disadvantages))
			.build();
	}

	/** Every subcategory just added gets its id. */
	@Override
	public DevilFruitType identified() {
		return toBuilder()
			.subcategories(this.subcategories.stream().map(DevilFruitTypeSubcategory::identified).toList())
			.build();
	}

	/** The languages of the type, of its paragraphs and of its subcategories. */
	@Override
	public Set<String> languages() {
		Set<String> languages = new TreeSet<>(this.translations.keySet());
		languages.addAll(paragraphLanguages());
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

	@Override
	public NaturalKey naturalKey() {
		return new NaturalKey(ROMAJI_FIELD, this.romaji);
	}

	/**
	 * The romaji and the first appearance, then a name per language, then a name and a
	 * description per language of each subcategory, then the paragraphs of every section
	 * and their points - every one missing.
	 */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.romaji == null) {
			missing.add(ROMAJI_FIELD);
		}
		if (lacksFirstAppearance()) {
			missing.add(FIELD);
		}
		for (String language : languages) {
			if (translationIn(language).name() == null) {
				missing.add(NAME_FIELD.formatted(language));
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
		missing.addAll(missingParagraphs(languages));
		missing.addAll(missingDiscovered());
		return missing;
	}

	@Override
	public Optional<StoryPoint> origin() {
		return Optional.of(this.firstAppearance);
	}

	@Override
	public Map<ParagraphSection, List<Paragraph>> paragraphs() {
		return new EnumMap<>(Map.of(ParagraphSection.DESCRIPTION, this.description, ParagraphSection.ADVANTAGES,
				this.advantages, ParagraphSection.DISADVANTAGES, this.disadvantages));
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

	/**
	 * Every point a response shows: its own first appearance and windows, then the first
	 * appearance of each subcategory; none chosen is left out.
	 */
	public List<StoryPoint> shownPoints() {
		Stream<StoryPoint> subcategoryPoints = this.subcategories.stream()
			.map(DevilFruitTypeSubcategory::firstAppearance)
			.filter(point -> !point.isMissing());
		return Stream.concat(points().stream(), subcategoryPoints).toList();
	}

	/** The subcategory with this id, when the type has it. */
	public Optional<DevilFruitTypeSubcategory> subcategory(UUID id) {
		return this.subcategories.stream().filter(subcategory -> id.equals(subcategory.id())).findFirst();
	}

}
