package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.readertime.FirstAppearing;
import dev.onepieceapi.contentservice.domain.readertime.NamedWindow;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.domain.readertime.Paragraphed;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.readertime.Revealing;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.WindowSequence;
import dev.onepieceapi.contentservice.domain.readertime.Windowed;
import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
import dev.onepieceapi.contentservice.domain.workflow.Text;
import lombok.Builder;

import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * What one version of a Character says (docs/user-flows/content-editorial-workflow.md
 * 3.1.11): a person of the story as of today (implementation plan of the Character, D1).
 * One romaji - the official Latin-script name - shared by every language, a name per
 * language code, its description as paragraphs (3.7.3), and its lists in the order they
 * are shown, each entry with its window on the reader axis (workflow revision 17, 3.7.2).
 * Plugged into the generic {@code Content} and {@code Version}, which carry the workflow
 * around it.
 *
 * @param japaneseName as published, not localized; never missing
 * @param status required to submit (D4)
 * @param gender required to submit (D4)
 * @param birthday day and month, no year (D5); never missing
 * @param epithets translated nicknames (D6); none is never null
 * @param aliases other names, the same in every language (D6); none is never null
 * @param species its species (D8), in order; none is "not stated"
 * @param affiliations its memberships (D10), in order; none is never null
 * @param devilFruits the fruits it ate (D12), in order; none is never null
 * @param relatives its relatives (D13), in order; none is never null
 * @param imageId its portrait (D7), optional and not localized
 * @param firstAppearance where it is first shown or named (D9); {@link StoryPoint#NONE}
 * for none, which a draft may keep - it is required to submit and to go online
 * @param description its biography, one paragraph per arc (3.7.3); none is never null
 * @param initialKnowledge what the reader believes at the first appearance (3.7.1); NONE
 * for nothing
 * @param reveals what the reader learns later, in list order; none is never null
 */
@Builder(toBuilder = true)
public record Character(String romaji, String japaneseName, Map<String, CharacterTranslation> translations,
		CharacterStatus status, Gender gender, MonthDay birthday, List<Epithet> epithets, List<Alias> aliases,
		List<SpeciesEntry> species, List<Membership> affiliations, List<FruitUse> devilFruits, List<Relative> relatives,
		String imageId, StoryPoint firstAppearance, List<Paragraph> description, CharacterKnowledge initialKnowledge,
		List<Reveal<CharacterField, CharacterKnowledge>> reveals)
		implements
			ContentBody<Character>,
			FirstAppearing,
			Paragraphed,
			Revealing<CharacterField, CharacterKnowledge> {

	public static final int ROMAJI_MAX_LENGTH = 100;

	public static final int NAME_MAX_LENGTH = 100;

	public static final int JAPANESE_NAME_MAX_LENGTH = 100;

	public static final int EPITHET_MAX_LENGTH = 100;

	public static final int ALIAS_MAX_LENGTH = 100;

	public static final int ROLE_MAX_LENGTH = 100;

	public static final int MAX_EPITHETS = 20;

	public static final int MAX_ALIASES = 20;

	/** Per membership (workflow 3.4.7). */
	public static final int MAX_PERIODS = 20;

	/**
	 * What the list is narrowed by (D16), as {@code ContentFilter#attributes} names it.
	 */
	public static final String STATUS_ATTRIBUTE = "characterStatus";

	public static final String SPECIES_ATTRIBUTE = "species";

	public static final String AFFILIATION_ATTRIBUTE = "affiliation";

	public static final String AFFILIATION_KIND_ATTRIBUTE = "affiliationKind";

	public static final String DEVIL_FRUIT_ATTRIBUTE = "devilFruit";

	private static final String ROMAJI_FIELD = "romaji";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String STATUS_FIELD = "status";

	private static final String GENDER_FIELD = "gender";

	private static final String NAME_FIELD = "translations[%s].name";

	private static final String EPITHET_FIELD = "epithets[%d].text[%s]";

	private static final String ROLE_FIELD = "affiliations[%d].periods[%d].role[%s]";

	private static final String PERIODS_FIELD = "affiliations[%d].periods";

	private static final String ENTRY_FIELD = "%s[%d]";

	/** Nothing chosen is {@link StoryPoint#NONE}, no list is null. */
	public Character {
		translations = translations == null ? Map.of() : translations;
		epithets = epithets == null ? List.of() : List.copyOf(epithets);
		aliases = aliases == null ? List.of() : List.copyOf(aliases);
		species = species == null ? List.of() : List.copyOf(species);
		affiliations = affiliations == null ? List.of() : List.copyOf(affiliations);
		devilFruits = devilFruits == null ? List.of() : List.copyOf(devilFruits);
		relatives = relatives == null ? List.of() : List.copyOf(relatives);
		firstAppearance = StoryPoint.orNone(firstAppearance);
		description = description == null ? List.of() : List.copyOf(description);
		initialKnowledge = (initialKnowledge == null) ? CharacterKnowledge.NONE : initialKnowledge;
		reveals = (reveals == null) ? List.of() : List.copyOf(reveals);
	}

	/** The same character with another portrait, or none. */
	public Character withImage(String newImageId) {
		return toBuilder().imageId(newImageId).build();
	}

	/**
	 * Every text without the space around it, a blank one counted as not written. An
	 * epithet or an alias left blank stays in its list, for the save to refuse it.
	 */
	@Override
	public Character normalized() {
		Map<String, CharacterTranslation> written = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			CharacterTranslation normalized = translation.normalized();
			if (!normalized.isEmpty()) {
				written.put(language, normalized);
			}
		});
		return toBuilder().romaji(Text.stripToNull(this.romaji))
			.japaneseName(Text.stripToNull(this.japaneseName))
			.translations(written)
			.epithets(this.epithets.stream().map(Epithet::normalized).toList())
			.aliases(this.aliases.stream().map(Alias::normalized).toList())
			.affiliations(this.affiliations.stream().map(Membership::normalized).toList())
			.description(Paragraph.normalized(this.description))
			.initialKnowledge(this.initialKnowledge.normalized())
			.reveals(Revealing.normalized(this.reveals))
			.build();
	}

	/**
	 * The languages of the character, of its paragraphs, of its epithets and of the roles
	 * of the periods of its memberships.
	 */
	@Override
	public Set<String> languages() {
		Set<String> languages = new TreeSet<>(this.translations.keySet());
		languages.addAll(paragraphLanguages());
		languages.addAll(revealLanguages());
		this.epithets.forEach(epithet -> languages.addAll(epithet.text().keySet()));
		this.affiliations
			.forEach(membership -> membership.periods().forEach(period -> languages.addAll(period.role().keySet())));
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
	 * The romaji, the status, the gender and the first appearance, then a name per
	 * language, then the text of each epithet per language, then each membership without
	 * a period and the role of each period that has one in some language, in the others,
	 * then the paragraphs (3.7.3), then the {@code discovered} point of every window
	 * (workflow 3.7.2) - every one missing. The Japanese name, the birthday, the rank,
	 * the {@code ended} points and the image are never missing.
	 */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.romaji == null) {
			missing.add(ROMAJI_FIELD);
		}
		if (this.status == null) {
			missing.add(STATUS_FIELD);
		}
		if (this.gender == null) {
			missing.add(GENDER_FIELD);
		}
		if (lacksFirstAppearance()) {
			missing.add(FIELD);
		}
		for (String language : languages) {
			if (translationIn(language).name() == null) {
				missing.add(NAME_FIELD.formatted(language));
			}
		}
		for (int index = 0; index < this.epithets.size(); index++) {
			Epithet epithet = this.epithets.get(index);
			for (String language : languages) {
				if (epithet.textIn(language) == null) {
					missing.add(EPITHET_FIELD.formatted(index, language));
				}
			}
		}
		for (int index = 0; index < this.affiliations.size(); index++) {
			missing.addAll(missingOfMembership(index, languages));
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
		return Map.of(ParagraphSection.DESCRIPTION, this.description);
	}

	/** No period at all, then the role of each period missing in a language. */
	private List<String> missingOfMembership(int index, Collection<String> languages) {
		List<MembershipPeriod> periods = this.affiliations.get(index).periods();
		List<String> missing = new ArrayList<>();
		if (periods.isEmpty()) {
			missing.add(PERIODS_FIELD.formatted(index));
		}
		for (int position = 0; position < periods.size(); position++) {
			MembershipPeriod period = periods.get(position);
			for (String language : languages) {
				if (period.hasRole() && period.roleIn(language) == null) {
					missing.add(ROLE_FIELD.formatted(index, position, language));
				}
			}
		}
		return missing;
	}

	/**
	 * Every window, list by list in the order of the request: epithets, aliases, species,
	 * the periods of each membership, devil fruits, relatives, paragraphs.
	 */
	@Override
	public List<NamedWindow> windows() {
		Stream<NamedWindow> periods = IntStream.range(0, this.affiliations.size())
			.boxed()
			.flatMap(index -> named(PERIODS_FIELD.formatted(index), this.affiliations.get(index).periods()));
		return Stream
			.of(named("epithets", this.epithets), named("aliases", this.aliases), named("species", this.species),
					periods, named("devilFruits", this.devilFruits), named("relatives", this.relatives),
					paragraphWindows().stream())
			.flatMap(Function.identity())
			.toList();
	}

	/** The periods of each membership follow one another (3.4.7). */
	@Override
	public List<WindowSequence> windowSequences() {
		return IntStream.range(0, this.affiliations.size())
			.mapToObj(index -> new WindowSequence(PERIODS_FIELD.formatted(index),
					this.affiliations.get(index).periods().stream().map(MembershipPeriod::window).toList()))
			.toList();
	}

	private static Stream<NamedWindow> named(String listField, List<? extends Windowed> entries) {
		return IntStream.range(0, entries.size())
			.mapToObj(index -> new NamedWindow(ENTRY_FIELD.formatted(listField, index), entries.get(index).window()));
	}

	/**
	 * What it says in this language - nothing at all, when no translation was written.
	 */
	@Override
	public Set<CharacterField> revealableFields() {
		return EnumSet.allOf(CharacterField.class);
	}

	public CharacterTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, CharacterTranslation.NONE);
	}

}
