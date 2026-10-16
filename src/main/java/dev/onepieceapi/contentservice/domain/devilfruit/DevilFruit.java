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
import java.util.function.Predicate;
import java.util.stream.Stream;

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
 * @param initialKnowledge what the reader knows at the first appearance of the fields a
 * reveal changes later (implementation plan of the timeline, T13);
 * {@link DevilFruitBelief#NONE} for none
 * @param reveals what the reader learns at each reveal (T13; ADR-0010), in the order of
 * the story; empty for none. The fields above stay the truth
 */
public record DevilFruit(String romaji, UUID typeContentId, UUID subcategoryId,
		Map<String, DevilFruitTranslation> translations, String imageId, StoryPoint firstAppearance,
		DevilFruitBelief initialKnowledge,
		List<DevilFruitReveal> reveals) implements ContentBody<DevilFruit>, Revealing {

	public static final int ROMAJI_MAX_LENGTH = 100;

	/** Where the initial knowledge is in a request. */
	public static final String INITIAL_KNOWLEDGE_PREFIX = "initialKnowledge.";

	/** Where a reveal is in a request, e.g. {@code reveals[2].}. */
	public static final String REVEAL_PREFIX = "reveals[%d].";

	private static final String ROMAJI_FIELD = "romaji";

	private static final String TYPE_FIELD = "type";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String TRANSLATED_FIELD = "translations[%s].%s";

	private static final String REVEAL_TITLE_FIELD = "reveals[%d].titles[%s]";

	/**
	 * Nothing chosen is {@link StoryPoint#NONE}, no initial knowledge
	 * {@link DevilFruitBelief#NONE}, no reveal an empty list.
	 */
	public DevilFruit {
		firstAppearance = StoryPoint.orNone(firstAppearance);
		initialKnowledge = initialKnowledge == null ? DevilFruitBelief.NONE : initialKnowledge;
		reveals = reveals == null ? List.of() : List.copyOf(reveals);
	}

	/** A fruit without reveals. */
	public DevilFruit(String romaji, UUID typeContentId, UUID subcategoryId,
			Map<String, DevilFruitTranslation> translations, String imageId, StoryPoint firstAppearance) {
		this(romaji, typeContentId, subcategoryId, translations, imageId, firstAppearance, DevilFruitBelief.NONE,
				List.of());
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
				this.firstAppearance, this.initialKnowledge, this.reveals);
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
				this.imageId, this.firstAppearance, this.initialKnowledge.normalized(),
				this.reveals.stream().map(DevilFruitReveal::normalized).toList());
	}

	/**
	 * Those of the fruit, of its initial knowledge and of its reveals: each is written
	 * per language.
	 */
	@Override
	public Set<String> languages() {
		Set<String> languages = new TreeSet<>(this.translations.keySet());
		languages.addAll(this.initialKnowledge.languages());
		this.reveals.forEach(reveal -> languages.addAll(reveal.languages()));
		return languages;
	}

	/** The names of the truth: those believed take no part in uniqueness. */
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
	 * advantages and disadvantages per language - every one missing; then every
	 * translated value the initial knowledge or a reveal holds, in every language it is
	 * missing from (T13), and the title of every reveal per language (T10). None of these
	 * is asked on save.
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
			for (DevilFruitField field : DevilFruitField.values()) {
				if (field.isTranslated() && field.in(translation) == null) {
					missing.add(TRANSLATED_FIELD.formatted(language, field.fieldName()));
				}
			}
		}
		missing.addAll(missingTranslations(INITIAL_KNOWLEDGE_PREFIX, this.initialKnowledge, languages));
		for (int index = 0; index < this.reveals.size(); index++) {
			DevilFruitReveal reveal = this.reveals.get(index);
			missing.addAll(missingTranslations(REVEAL_PREFIX.formatted(index), reveal.values(), languages));
			for (String language : languages) {
				if (reveal.titleIn(language) == null) {
					missing.add(REVEAL_TITLE_FIELD.formatted(index, language));
				}
			}
		}
		return missing;
	}

	private static List<String> missingTranslations(String prefix, DevilFruitBelief belief,
			Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		for (DevilFruitField field : DevilFruitField.values()) {
			if (field.isTranslated() && belief.holds(field)) {
				languages.stream()
					.filter(language -> !belief.holds(field, language))
					.forEach(language -> missing.add(prefix + TRANSLATED_FIELD.formatted(language, field.fieldName())));
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
	 * The type and subcategory of the fruit, then those of its initial knowledge and of
	 * each reveal holding them.
	 */
	public List<DevilFruitClassification> classifications() {
		List<DevilFruitClassification> classifications = new ArrayList<>();
		classifications.add(new DevilFruitClassification(this.typeContentId, this.subcategoryId, null, false));
		if (this.initialKnowledge.holds(DevilFruitField.TYPE)) {
			classifications.add(new DevilFruitClassification(this.initialKnowledge.typeContentId(),
					this.initialKnowledge.subcategoryId(), null, true));
		}
		for (int index = 0; index < this.reveals.size(); index++) {
			DevilFruitBelief values = this.reveals.get(index).values();
			if (values.holds(DevilFruitField.TYPE)) {
				classifications
					.add(new DevilFruitClassification(values.typeContentId(), values.subcategoryId(), index, false));
			}
		}
		return classifications;
	}

	/** The types its initial knowledge and its reveals hold, those chosen only. */
	public List<UUID> beliefTypeIds() {
		return Stream.concat(Stream.of(this.initialKnowledge), this.reveals.stream().map(DevilFruitReveal::values))
			.map(DevilFruitBelief::typeContentId)
			.filter(Objects::nonNull)
			.toList();
	}

	/** The positions of the reveals naming this field, in order. */
	public List<Integer> revealsOf(DevilFruitField field) {
		List<Integer> positions = new ArrayList<>();
		for (int index = 0; index < this.reveals.size(); index++) {
			if (this.reveals.get(index).reveals(field)) {
				positions.add(index);
			}
		}
		return positions;
	}

	/**
	 * The fruit as a reader knows it having reached these reveals (implementation plan of
	 * the timeline, T13): a field no reveal names is the truth; otherwise the value of
	 * the last reveal reached naming it - the truth when it holds none - else the initial
	 * knowledge, unknown (null) when that holds none either. The type and the subcategory
	 * go together. Neither initial knowledge nor reveal is left on it. Whether the values
	 * themselves are revealed yet - a type first appearing later - is the reader's filter
	 * to say, not the fruit's.
	 * @param reached the reveals reached, e.g. {@link Reveals#reachedAt}
	 */
	public DevilFruit knownAt(Predicate<? super Reveal> reached) {
		DevilFruitBelief type = sourceOf(DevilFruitField.TYPE, reached);
		Map<String, DevilFruitTranslation> known = new TreeMap<>();
		languages().forEach(language -> {
			DevilFruitTranslation translation = new DevilFruitTranslation(
					knownIn(DevilFruitField.NAME, language, reached),
					knownIn(DevilFruitField.DESCRIPTION, language, reached),
					knownIn(DevilFruitField.ADVANTAGES, language, reached),
					knownIn(DevilFruitField.DISADVANTAGES, language, reached));
			if (!translation.isEmpty()) {
				known.put(language, translation);
			}
		});
		return new DevilFruit(sourceOf(DevilFruitField.ROMAJI, reached).romaji(), type.typeContentId(),
				type.subcategoryId(), known, this.imageId, this.firstAppearance, DevilFruitBelief.NONE, List.of());
	}

	private String knownIn(DevilFruitField field, String language, Predicate<? super Reveal> reached) {
		return field.in(sourceOf(field, reached).translationIn(language));
	}

	/**
	 * Where a field reads from: the truth, the values of a reveal or the initial
	 * knowledge. A reveal holding no value for it - the last one - gives the truth.
	 */
	private DevilFruitBelief sourceOf(DevilFruitField field, Predicate<? super Reveal> reached) {
		DevilFruitBelief truth = new DevilFruitBelief(this.romaji, this.typeContentId, this.subcategoryId,
				this.translations);
		if (!Reveals.named(this.reveals, reveal -> reveal.reveals(field))) {
			return truth;
		}
		return Reveals.lastReached(this.reveals, reached, reveal -> reveal.reveals(field))
			.map(reveal -> reveal.values().holds(field) ? reveal.values() : truth)
			.orElse(this.initialKnowledge);
	}

}
