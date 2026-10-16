package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.readertime.Reveals;
import dev.onepieceapi.contentservice.domain.readertime.Revealing;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * What one version of a Devil Fruit says (implementation plan of the Devil Fruit, D1,
 * D7): one romaji shared by every language, the Devil Fruit Type it belongs to, and a
 * name, description, advantages and disadvantages per language code. Plugged into the
 * generic {@code Content} and {@code Version}, which carry the workflow around it.
 * <p>
 * The type is an id - the type's content, not one of its versions - and is missing while
 * the version is an incomplete draft. Two versions saying the same except for the type
 * are different, as the record's equality has it.
 *
 * @param typeContentId the content of the Devil Fruit Type; null while not chosen
 * @param subcategoryId one subcategory of that type (implementation plan of the
 * subcategories, S5); null for none, which is complete
 * @param imageId its image (implementation plan of the Devil Fruit, D5), optional and not
 * localized; part of the version, so a different image is a different version
 * @param firstAppearance the chapter the reader first meets it in (implementation plan of
 * the timeline, T7); {@link StoryPoint#NONE} for none, which is complete - it is required
 * only to go online
 * @param reveals what the reader believed before each reveal (implementation plan of the
 * timeline, T9; ADR-0010), in the order of the story; empty for none. The fields above
 * stay the truth
 */
public record DevilFruit(String romaji, UUID typeContentId, UUID subcategoryId,
		Map<String, DevilFruitTranslation> translations, String imageId, StoryPoint firstAppearance,
		List<DevilFruitReveal> reveals) implements ContentBody<DevilFruit>, Revealing {

	public static final int ROMAJI_MAX_LENGTH = 100;

	private static final String ROMAJI_FIELD = "romaji";

	private static final String TYPE_FIELD = "type";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String NAME_FIELD = "translations[%s].name";

	private static final String DESCRIPTION_FIELD = "translations[%s].description";

	private static final String ADVANTAGES_FIELD = "translations[%s].advantages";

	private static final String DISADVANTAGES_FIELD = "translations[%s].disadvantages";

	private static final String REVEAL_DESCRIPTION_FIELD = "reveals[%d].translations[%s].description";

	private static final String REVEAL_TITLE_FIELD = "reveals[%d].titles[%s]";

	/** Nothing chosen is {@link StoryPoint#NONE}, no reveal an empty list. */
	public DevilFruit {
		firstAppearance = StoryPoint.orNone(firstAppearance);
		reveals = reveals == null ? List.of() : List.copyOf(reveals);
	}

	/** A fruit without reveals. */
	public DevilFruit(String romaji, UUID typeContentId, UUID subcategoryId,
			Map<String, DevilFruitTranslation> translations, String imageId, StoryPoint firstAppearance) {
		this(romaji, typeContentId, subcategoryId, translations, imageId, firstAppearance, List.of());
	}

	/** A fruit without a first appearance. */
	public DevilFruit(String romaji, UUID typeContentId, UUID subcategoryId,
			Map<String, DevilFruitTranslation> translations, String imageId) {
		this(romaji, typeContentId, subcategoryId, translations, imageId, StoryPoint.NONE);
	}

	/** A fruit without a subcategory or an image. */
	public DevilFruit(String romaji, UUID typeContentId, Map<String, DevilFruitTranslation> translations) {
		this(romaji, typeContentId, null, translations, null);
	}

	/** A fruit without a subcategory. */
	public DevilFruit(String romaji, UUID typeContentId, Map<String, DevilFruitTranslation> translations,
			String imageId) {
		this(romaji, typeContentId, null, translations, imageId);
	}

	/** The same fruit with another image, or none. */
	public DevilFruit withImage(String newImageId) {
		return new DevilFruit(this.romaji, this.typeContentId, this.subcategoryId, this.translations, newImageId,
				this.firstAppearance, this.reveals);
	}

	@Override
	public DevilFruit normalized() {
		Map<String, DevilFruitTranslation> written = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			DevilFruitTranslation normalized = translation.normalized();
			if (!normalized.isEmpty()) {
				written.put(language, normalized);
			}
		});
		return new DevilFruit(Text.stripToNull(this.romaji), this.typeContentId, this.subcategoryId, written,
				this.imageId, this.firstAppearance, this.reveals.stream().map(DevilFruitReveal::normalized).toList());
	}

	/** Those of the fruit and of its reveals: a reveal too is written per language. */
	@Override
	public Set<String> languages() {
		Set<String> languages = new TreeSet<>(this.translations.keySet());
		this.reveals.forEach(reveal -> languages.addAll(reveal.languages()));
		return languages;
	}

	/** The names of the truth: those of the reveals take no part in uniqueness. */
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
	public Optional<NaturalKey> naturalKey() {
		return Optional.of(new NaturalKey(ROMAJI_FIELD, this.romaji));
	}

	/**
	 * The romaji, the type and the first appearance, then a name, a description,
	 * advantages and disadvantages per language - every one missing; then the description
	 * of the last reveal per language, so no reader before the last reveal meets the
	 * whole truth (T9); and the title of every reveal per language (T10). Neither is
	 * asked on save.
	 */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.romaji == null) {
			missing.add(ROMAJI_FIELD);
		}
		if (this.typeContentId == null) {
			missing.add(TYPE_FIELD);
		}
		if (lacksFirstAppearance()) {
			missing.add(FIELD);
		}
		for (String language : languages) {
			DevilFruitTranslation translation = translationIn(language);
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
		if (!this.reveals.isEmpty()) {
			int last = this.reveals.size() - 1;
			languages.stream()
				.filter(language -> this.reveals.get(last).translationIn(language).description() == null)
				.forEach(language -> missing.add(REVEAL_DESCRIPTION_FIELD.formatted(last, language)));
		}
		for (int index = 0; index < this.reveals.size(); index++) {
			DevilFruitReveal reveal = this.reveals.get(index);
			for (String language : languages) {
				if (reveal.titleIn(language) == null) {
					missing.add(REVEAL_TITLE_FIELD.formatted(index, language));
				}
			}
		}
		return missing;
	}

	/**
	 * What it says in this language - nothing at all, when no translation was written.
	 */
	public DevilFruitTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, DevilFruitTranslation.NONE);
	}

	/**
	 * The type in force at a reveal: its own, else that of the first later reveal
	 * defining one, else the truth.
	 */
	public UUID typeInForceAt(int reveal) {
		return Reveals.resolve(this.reveals.subList(reveal, this.reveals.size()), any -> true,
				DevilFruitReveal::typeContentId, this.typeContentId);
	}

	/**
	 * The type and subcategory of the fruit, then those of each reveal defining them,
	 * with the type in force there.
	 */
	public List<DevilFruitClassification> classifications() {
		List<DevilFruitClassification> classifications = new ArrayList<>();
		classifications.add(new DevilFruitClassification(this.typeContentId, this.subcategoryId, null));
		IntStream.range(0, this.reveals.size())
			.filter(index -> this.reveals.get(index).definesClassification())
			.forEach(index -> classifications.add(new DevilFruitClassification(typeInForceAt(index),
					this.reveals.get(index).subcategoryId(), index)));
		return classifications;
	}

	/** The types its reveals define, those chosen only. */
	public List<UUID> revealTypeIds() {
		return this.reveals.stream().map(DevilFruitReveal::typeContentId).filter(Objects::nonNull).toList();
	}

	/**
	 * The fruit as a reader believes it where only these reveals are still believed
	 * (implementation plan of the timeline, T9): each field from the first such reveal
	 * defining it, else the truth; the type and the subcategory together. No reveal is
	 * left on it. Whether the values themselves are revealed yet - a type first appearing
	 * later - is the reader's filter to say, not the fruit's.
	 * @param believed the reveals still believed, e.g. {@link Reveals#believedAt}
	 */
	public DevilFruit asBelieved(Predicate<? super Reveal> believed) {
		UUID type = Reveals.resolve(this.reveals, believed, DevilFruitReveal::typeContentId, this.typeContentId);
		UUID subcategory = Reveals.resolve(this.reveals, believed, DevilFruitReveal::definesClassification,
				DevilFruitReveal::subcategoryId, this.subcategoryId);
		Map<String, DevilFruitTranslation> believedTranslations = new TreeMap<>();
		languages().forEach(language -> {
			DevilFruitTranslation translation = new DevilFruitTranslation(
					believed(believed, language, DevilFruitTranslation::name),
					believed(believed, language, DevilFruitTranslation::description),
					believed(believed, language, DevilFruitTranslation::advantages),
					believed(believed, language, DevilFruitTranslation::disadvantages));
			if (!translation.isEmpty()) {
				believedTranslations.put(language, translation);
			}
		});
		return new DevilFruit(Reveals.resolve(this.reveals, believed, DevilFruitReveal::romaji, this.romaji), type,
				subcategory, believedTranslations, this.imageId, this.firstAppearance, List.of());
	}

	private String believed(Predicate<? super Reveal> believed, String language,
			Function<DevilFruitTranslation, String> field) {
		return Reveals.resolve(this.reveals, believed, reveal -> field.apply(reveal.translationIn(language)),
				field.apply(translationIn(language)));
	}

}
