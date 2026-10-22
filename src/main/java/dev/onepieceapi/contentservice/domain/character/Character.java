package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.readertime.FirstAppearing;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
import dev.onepieceapi.contentservice.domain.workflow.Text;
import lombok.Builder;

import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * What one version of a Character says (docs/user-flows/content-editorial-workflow.md
 * 3.1.11): a person of the story as of today (implementation plan of the Character, D1).
 * One romaji - the official Latin-script name - shared by every language, a name and a
 * description per language code, and its lists in the order they are shown. Plugged into
 * the generic {@code Content} and {@code Version}, which carry the workflow around it.
 *
 * @param japaneseName as published, not localized; never missing
 * @param status required to submit (D4)
 * @param gender required to submit (D4)
 * @param birthday day and month, no year (D5); never missing
 * @param epithets translated nicknames (D6); none is never null
 * @param aliases other names, the same in every language (D6); none is never null
 * @param speciesIds the content ids of its species (D8), in order; none is "not stated"
 * @param affiliations its memberships (D10), in order; none is never null
 * @param imageId its portrait (D7), optional and not localized
 * @param firstAppearance where it is first shown or named (D9); {@link StoryPoint#NONE}
 * for none, which a draft may keep - it is required to submit and to go online
 */
@Builder(toBuilder = true)
public record Character(String romaji, String japaneseName, Map<String, CharacterTranslation> translations,
		CharacterStatus status, Gender gender, MonthDay birthday, List<Epithet> epithets, List<String> aliases,
		List<UUID> speciesIds, List<Membership> affiliations, String imageId,
		StoryPoint firstAppearance) implements ContentBody<Character>, FirstAppearing {

	public static final int ROMAJI_MAX_LENGTH = 100;

	public static final int NAME_MAX_LENGTH = 100;

	public static final int JAPANESE_NAME_MAX_LENGTH = 100;

	public static final int DESCRIPTION_MAX_LENGTH = 2000;

	public static final int EPITHET_MAX_LENGTH = 100;

	public static final int ALIAS_MAX_LENGTH = 100;

	public static final int ROLE_MAX_LENGTH = 100;

	public static final int MAX_EPITHETS = 20;

	public static final int MAX_ALIASES = 20;

	/**
	 * What the list is narrowed by (D16), as {@code ContentFilter#attributes} names it.
	 */
	public static final String STATUS_ATTRIBUTE = "characterStatus";

	public static final String SPECIES_ATTRIBUTE = "species";

	public static final String AFFILIATION_ATTRIBUTE = "affiliation";

	public static final String AFFILIATION_KIND_ATTRIBUTE = "affiliationKind";

	private static final String ROMAJI_FIELD = "romaji";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String STATUS_FIELD = "status";

	private static final String GENDER_FIELD = "gender";

	private static final String NAME_FIELD = "translations[%s].name";

	private static final String DESCRIPTION_FIELD = "translations[%s].description";

	private static final String EPITHET_FIELD = "epithets[%d].text[%s]";

	private static final String ROLE_FIELD = "affiliations[%d].role[%s]";

	/** Nothing chosen is {@link StoryPoint#NONE}, no list is null. */
	public Character {
		translations = translations == null ? Map.of() : translations;
		epithets = epithets == null ? List.of() : List.copyOf(epithets);
		aliases = aliases == null ? List.of() : List.copyOf(aliases);
		speciesIds = speciesIds == null ? List.of() : List.copyOf(speciesIds);
		affiliations = affiliations == null ? List.of() : List.copyOf(affiliations);
		firstAppearance = StoryPoint.orNone(firstAppearance);
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
			.aliases(this.aliases.stream().map(String::strip).toList())
			.affiliations(this.affiliations.stream().map(Membership::normalized).toList())
			.build();
	}

	/**
	 * The languages of the character, of its epithets and of the roles of its
	 * memberships.
	 */
	@Override
	public Set<String> languages() {
		Set<String> languages = new TreeSet<>(this.translations.keySet());
		this.epithets.forEach(epithet -> languages.addAll(epithet.text().keySet()));
		this.affiliations.forEach(membership -> languages.addAll(membership.role().keySet()));
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
	 * The romaji, the status, the gender and the first appearance, then a name and a
	 * description per language, then the text of each epithet per language, then the role
	 * of each membership that has one in some language, in the others - every one
	 * missing. The Japanese name, the birthday, the aliases, the species, the rank, the
	 * former flag and the image are never missing.
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
			CharacterTranslation translation = translationIn(language);
			if (translation.name() == null) {
				missing.add(NAME_FIELD.formatted(language));
			}
			if (translation.description() == null) {
				missing.add(DESCRIPTION_FIELD.formatted(language));
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
			Membership membership = this.affiliations.get(index);
			for (String language : languages) {
				if (membership.hasRole() && membership.roleIn(language) == null) {
					missing.add(ROLE_FIELD.formatted(index, language));
				}
			}
		}
		return missing;
	}

	/**
	 * What it says in this language - nothing at all, when no translation was written.
	 */
	public CharacterTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, CharacterTranslation.NONE);
	}

}
