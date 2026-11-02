package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.readertime.FirstAppearing;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.domain.readertime.Paragraphed;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.readertime.Revealing;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
import dev.onepieceapi.contentservice.domain.workflow.Text;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * What one version of a Devil Fruit says (implementation plan of the Devil Fruit, D1,
 * D7): one romaji shared by every language, the Devil Fruit Type it belongs to, a name
 * per language code, and its description, advantages and disadvantages as paragraphs
 * (workflow 3.7.3). Plugged into the generic {@code Content} and {@code Version}, which
 * carry the workflow around it.
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
 * @param multipleUsers whether several characters may use it at the same time, as the
 * SMILE fruits (implementation plan of the Character, D12); never missing, false unless
 * said
 * @param description its paragraphs, in order; none is never null
 * @param advantages as {@code description}
 * @param disadvantages as {@code description}
 * @param initialKnowledge what the reader believes at the first appearance (workflow
 * 3.7.1); {@link DevilFruitKnowledge#NONE} for nothing
 * @param reveals what the reader learns later, in list order; none is never null
 */
@Builder(toBuilder = true)
public record DevilFruit(String romaji, UUID typeContentId, UUID subcategoryId,
		Map<String, DevilFruitTranslation> translations, String imageId, StoryPoint firstAppearance,
		boolean multipleUsers, List<Paragraph> description, List<Paragraph> advantages, List<Paragraph> disadvantages,
		DevilFruitKnowledge initialKnowledge, List<Reveal<DevilFruitField, DevilFruitKnowledge>> reveals)
		implements
			ContentBody<DevilFruit>,
			FirstAppearing,
			Paragraphed,
			Revealing<DevilFruitField, DevilFruitKnowledge> {

	public static final int ROMAJI_MAX_LENGTH = 100;

	private static final String ROMAJI_FIELD = "romaji";

	private static final String TYPE_FIELD = "type";

	/** A reveal, as the detail of a block names it. */
	private static final String REVEAL_INDEX = "revealIndex";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String NAME_FIELD = "translations[%s].name";

	/** Nothing chosen is {@link StoryPoint#NONE}, not null; no list is null. */
	public DevilFruit {
		translations = translations == null ? Map.of() : translations;
		firstAppearance = StoryPoint.orNone(firstAppearance);
		description = description == null ? List.of() : List.copyOf(description);
		advantages = advantages == null ? List.of() : List.copyOf(advantages);
		disadvantages = disadvantages == null ? List.of() : List.copyOf(disadvantages);
		initialKnowledge = (initialKnowledge == null) ? DevilFruitKnowledge.NONE : initialKnowledge;
		reveals = (reveals == null) ? List.of() : List.copyOf(reveals);
	}

	/** The same fruit with another image, or none. */
	public DevilFruit withImage(String newImageId) {
		return toBuilder().imageId(newImageId).build();
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
		return toBuilder().romaji(Text.stripToNull(this.romaji))
			.translations(written)
			.description(Paragraph.normalized(this.description))
			.advantages(Paragraph.normalized(this.advantages))
			.disadvantages(Paragraph.normalized(this.disadvantages))
			.initialKnowledge(this.initialKnowledge.normalized())
			.reveals(Revealing.normalized(this.reveals))
			.build();
	}

	@Override
	public Set<String> languages() {
		Set<String> languages = new TreeSet<>(this.translations.keySet());
		languages.addAll(paragraphLanguages());
		languages.addAll(revealLanguages());
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
	 * The romaji, the type and the first appearance, then a name per language, then the
	 * paragraphs of every section and their points - every one missing.
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
			if (translationIn(language).name() == null) {
				missing.add(NAME_FIELD.formatted(language));
			}
		}
		missing.addAll(missingParagraphs(languages));
		missing.addAll(missingDiscovered());
		missing.addAll(missingReveals(languages));
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

	@Override
	public Set<DevilFruitField> revealableFields() {
		return EnumSet.allOf(DevilFruitField.class);
	}

	/**
	 * The types its initial knowledge and its reveals hold (workflow 3.7.1), each named
	 * where a request names it, in that order.
	 */
	public List<TypeChoice> revealedTypes() {
		List<TypeChoice> choices = new ArrayList<>();
		choices.add(new TypeChoice(INITIAL_KNOWLEDGE + ".", Map.of(INITIAL_KNOWLEDGE, true), this.initialKnowledge));
		for (int index = 0; index < this.reveals.size(); index++) {
			choices.add(new TypeChoice(REVEAL_FIELD.formatted(index) + ".", Map.of(REVEAL_INDEX, index),
					this.reveals.get(index).values()));
		}
		return choices.stream().filter(TypeChoice::isHeld).toList();
	}

	/**
	 * What it says in this language - nothing at all, when no translation was written.
	 */
	public DevilFruitTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, DevilFruitTranslation.NONE);
	}

}
