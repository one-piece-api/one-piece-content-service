package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.Epithet;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.AffiliationVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The rules a Character must meet (docs/user-flows/content-editorial-workflow.md 3.3,
 * 3.1.11): those of every content - see {@link ContentValidator} - and those of its lists
 * (implementation plan of the Character, D6, D8): at most {@value Character#MAX_EPITHETS}
 * epithets and {@value Character#MAX_ALIASES} aliases, none blank; within the character,
 * an epithet unique per language and an alias unique, whatever the case; a species named
 * once, each one it may be linked to; an affiliation named once in its memberships, each
 * one it may be linked to, a rank only in an affiliation that, as last approved, is a
 * Navy one (D10). Its first appearance, when chosen, is in a chapter and an episode it
 * may be linked to (3.4.3). Each entry at fault is reported, the later of two equal ones.
 * Its portrait is checked against the {@code character} image profile when uploaded, as
 * every image. A birthday is always a real date: a {@code MonthDay} cannot be anything
 * else.
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

	private static final String REPEATED_AFFILIATION = "is another affiliation of this character";

	private static final String RANK_NOT_ALLOWED = "is allowed only in a Navy affiliation";

	private static final String MEMBERSHIP_FIELD = "%s[%d].%s";

	private static final String AFFILIATION_PART = "affiliation";

	private static final String MARINE_RANK_PART = "marineRank";

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	private final SpeciesVersionRepository speciesRepository;

	private final AffiliationVersionRepository affiliationRepository;

	@Autowired
	public CharacterValidator(CharacterVersionRepository versionRepository, LanguageRepository languageRepository,
			ChapterVersionRepository chapterRepository, EpisodeVersionRepository episodeRepository,
			SpeciesVersionRepository speciesRepository, AffiliationVersionRepository affiliationRepository) {
		super(versionRepository, languageRepository, CharacterVersionMapper::toDomain);
		this.chapterRepository = chapterRepository;
		this.episodeRepository = episodeRepository;
		this.speciesRepository = speciesRepository;
		this.affiliationRepository = affiliationRepository;
	}

	@Override
	protected void requireValidFields(Character body) {
		List<FieldViolation> violations = new ArrayList<>();
		requireAtMost(EPITHETS, body.epithets(), Character.MAX_EPITHETS, violations);
		requireAtMost(ALIASES, body.aliases(), Character.MAX_ALIASES, violations);
		requireValidEpithets(body.epithets(), violations);
		requireValidAliases(body.aliases(), violations);
		requireDistinctSpecies(body.speciesIds(), violations);
		requireDistinctAffiliations(body.affiliations(), violations);
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
		violations.addAll(membershipViolations(body.affiliations()));
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

	private static void requireDistinctAffiliations(List<Membership> memberships, List<FieldViolation> violations) {
		Set<UUID> seen = new HashSet<>();
		for (int index = 0; index < memberships.size(); index++) {
			if (!seen.add(memberships.get(index).affiliationContentId())) {
				violations.add(new FieldViolation(
						MEMBERSHIP_FIELD.formatted(CharacterRelations.AFFILIATIONS_FIELD, index, AFFILIATION_PART),
						REPEATED_AFFILIATION));
			}
		}
	}

	/**
	 * Each membership whose affiliation may not be linked, and each rank held in an
	 * affiliation that is not a Navy one - by its latest approved version, which an
	 * affiliation may leave by going online as another kind: publishing checks it again.
	 */
	private List<FieldViolation> membershipViolations(List<Membership> memberships) {
		List<FieldViolation> violations = new ArrayList<>();
		Map<UUID, AffiliationKind> kinds = approvedKindsOf(memberships);
		for (int index = 0; index < memberships.size(); index++) {
			Membership membership = memberships.get(index);
			String prefix = "%s[%d].".formatted(CharacterRelations.AFFILIATIONS_FIELD, index);
			var notLinkable = RelationRules.notLinkable(CharacterRelations.AFFILIATION, membership, prefix,
					this.affiliationRepository);
			notLinkable.ifPresent(violations::add);
			if (notLinkable.isEmpty() && membership.marineRank() != null
					&& kinds.get(membership.affiliationContentId()) != AffiliationKind.NAVY) {
				violations.add(new FieldViolation(prefix + MARINE_RANK_PART, RANK_NOT_ALLOWED));
			}
		}
		return violations;
	}

	/**
	 * The kind of the affiliations that hold a rank, as last approved; none read for no
	 * rank.
	 */
	private Map<UUID, AffiliationKind> approvedKindsOf(List<Membership> memberships) {
		List<UUID> ranked = memberships.stream()
			.filter(membership -> membership.marineRank() != null)
			.map(Membership::affiliationContentId)
			.toList();
		Map<UUID, AffiliationKind> kinds = new HashMap<>();
		if (!ranked.isEmpty()) {
			for (AffiliationVersionEntity version : this.affiliationRepository.findMostRecent(ranked,
					VersionStatus.approved())) {
				kinds.put(version.getContentId(), AffiliationVersionMapper.toDomain(version).body().kind());
			}
		}
		return kinds;
	}

	private static String entryField(String listField, int index) {
		return ENTRY_FIELD.formatted(listField, index);
	}

}
