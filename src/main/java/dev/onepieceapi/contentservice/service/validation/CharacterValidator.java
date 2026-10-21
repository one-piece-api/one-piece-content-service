package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.Epithet;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.CharacterVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.service.character.CharacterRelations;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.exception.web.FieldViolation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The rules a Character must meet (docs/user-flows/content-editorial-workflow.md 3.3,
 * 3.1.11): those of every content - see {@link ContentValidator} - and those of its lists
 * (implementation plan of the Character, D6, D8): at most {@value Character#MAX_EPITHETS}
 * epithets and {@value Character#MAX_ALIASES} aliases, none blank; within the character,
 * an epithet unique per language and an alias unique, whatever the case; a species named
 * once, each one it may be linked to. Its first appearance, when chosen, is in a chapter
 * and an episode it may be linked to (3.4.3). Each entry at fault is reported, the later
 * of two equal ones. Its portrait is checked against the {@code character} image profile
 * when uploaded, as every image. A birthday is always a real date: a {@code MonthDay}
 * cannot be anything else.
 */
@Component
public class CharacterValidator extends ContentValidator<Character, CharacterVersionEntity> {

	private static final String ENTRY_FIELD = "%s[%d]";

	private static final String EPITHET_TEXT_FIELD = "epithets[%d].text[%s]";

	private static final String EPITHETS = "epithets";

	private static final String ALIASES = "aliases";

	private static final String TOO_MANY = "must have at most %d entries";

	private static final String BLANK = "must not be blank";

	private static final String REPEATED_EPITHET = "is another epithet of this character";

	private static final String REPEATED_ALIAS = "is another alias of this character";

	private static final String REPEATED_SPECIES = "is another species of this character";

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	private final SpeciesVersionRepository speciesRepository;

	@Autowired
	public CharacterValidator(CharacterVersionRepository versionRepository, LanguageRepository languageRepository,
			ChapterVersionRepository chapterRepository, EpisodeVersionRepository episodeRepository,
			SpeciesVersionRepository speciesRepository) {
		super(versionRepository, languageRepository, CharacterVersionMapper::toDomain);
		this.chapterRepository = chapterRepository;
		this.episodeRepository = episodeRepository;
		this.speciesRepository = speciesRepository;
	}

	@Override
	protected void requireValidFields(Character body) {
		List<FieldViolation> violations = new ArrayList<>();
		requireAtMost(EPITHETS, body.epithets(), Character.MAX_EPITHETS, violations);
		requireAtMost(ALIASES, body.aliases(), Character.MAX_ALIASES, violations);
		requireValidEpithets(body.epithets(), violations);
		requireValidAliases(body.aliases(), violations);
		requireDistinctSpecies(body.speciesIds(), violations);
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
	}

	/** Every first appearance and every species at fault is reported. */
	@Override
	protected void requireValidRelations(Character body) {
		List<FieldViolation> violations = new ArrayList<>();
		RelationRules.notLinkable(CharacterRelations.FIRST_APPEARANCE_CHAPTER, body, "", this.chapterRepository)
			.ifPresent(violations::add);
		RelationRules.notLinkable(CharacterRelations.FIRST_APPEARANCE_EPISODE, body, "", this.episodeRepository)
			.ifPresent(violations::add);
		violations.addAll(RelationRules.notLinkable(CharacterRelations.SPECIES, body.speciesIds(),
				CharacterRelations.SPECIES_FIELD, this.speciesRepository));
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
	}

	private static void requireAtMost(String listField, List<?> entries, int max, List<FieldViolation> violations) {
		if (entries.size() > max) {
			violations.add(new FieldViolation(listField, TOO_MANY.formatted(max)));
		}
	}

	/**
	 * An epithet written in no language is blank; equal texts are compared per language.
	 */
	private static void requireValidEpithets(List<Epithet> epithets, List<FieldViolation> violations) {
		Set<String> seen = new HashSet<>();
		for (int index = 0; index < epithets.size(); index++) {
			Epithet epithet = epithets.get(index);
			if (epithet.isEmpty()) {
				violations.add(new FieldViolation(entryField(EPITHETS, index), BLANK));
			}
			int position = index;
			epithet.text().forEach((language, text) -> {
				if (!seen.add(language + ':' + text.toLowerCase(Locale.ROOT))) {
					violations
						.add(new FieldViolation(EPITHET_TEXT_FIELD.formatted(position, language), REPEATED_EPITHET));
				}
			});
		}
	}

	private static void requireValidAliases(List<String> aliases, List<FieldViolation> violations) {
		Set<String> seen = new HashSet<>();
		for (int index = 0; index < aliases.size(); index++) {
			String alias = aliases.get(index);
			if (alias.isBlank()) {
				violations.add(new FieldViolation(entryField(ALIASES, index), BLANK));
			}
			else if (!seen.add(alias.toLowerCase(Locale.ROOT))) {
				violations.add(new FieldViolation(entryField(ALIASES, index), REPEATED_ALIAS));
			}
		}
	}

	private static void requireDistinctSpecies(List<UUID> speciesIds, List<FieldViolation> violations) {
		Set<UUID> seen = new HashSet<>();
		for (int index = 0; index < speciesIds.size(); index++) {
			if (!seen.add(speciesIds.get(index))) {
				violations
					.add(new FieldViolation(entryField(CharacterRelations.SPECIES_FIELD, index), REPEATED_SPECIES));
			}
		}
	}

	private static String entryField(String listField, int index) {
		return ENTRY_FIELD.formatted(listField, index);
	}

}
