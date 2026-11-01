package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.affiliation.AffiliationReference;
import dev.onepieceapi.contentservice.domain.character.Alias;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterLookups;
import dev.onepieceapi.contentservice.domain.character.CharacterReference;
import dev.onepieceapi.contentservice.domain.character.CharacterTranslation;
import dev.onepieceapi.contentservice.domain.character.Epithet;
import dev.onepieceapi.contentservice.domain.character.FruitUse;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.character.MembershipPeriod;
import dev.onepieceapi.contentservice.domain.character.Relative;
import dev.onepieceapi.contentservice.domain.character.RelativeOf;
import dev.onepieceapi.contentservice.domain.character.SpeciesEntry;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReference;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.species.SpeciesReference;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.web.dto.response.AffiliationReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.AliasResponse;
import dev.onepieceapi.contentservice.web.dto.response.CharacterNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.CharacterReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.CharacterResponse;
import dev.onepieceapi.contentservice.web.dto.response.CharacterTranslationResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.EpithetResponse;
import dev.onepieceapi.contentservice.web.dto.response.FirstAppearanceResponse;
import dev.onepieceapi.contentservice.web.dto.response.FruitUseResponse;
import dev.onepieceapi.contentservice.web.dto.response.MembershipPeriodResponse;
import dev.onepieceapi.contentservice.web.dto.response.MembershipResponse;
import dev.onepieceapi.contentservice.web.dto.response.RelativeResponse;
import dev.onepieceapi.contentservice.web.dto.response.SpeciesEntryResponse;
import dev.onepieceapi.contentservice.web.dto.response.SpeciesReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * From Characters to their response bodies: what a version says is mapped here, the
 * workflow around it by {@link ContentResponseMapper}. The species, the affiliations, the
 * chapters and the episodes it points to - its first appearance and the points of its
 * windows - are given already looked up, so the mapper reads no database.
 */
@UtilityClass
public class CharacterResponseMapper {

	/** A version of a Character as its caller meets it, with everything it says. */
	public VersionResponse<CharacterResponse> toVersionResponse(VersionAccess<Character> access,
			CharacterLookups lookups, Map<UUID, NumberedReference> storyItems, ImageResponseMapper images) {
		return ContentResponseMapper.toVersionResponse(access,
				character -> toResponse(character, lookups, storyItems, images));
	}

	/**
	 * A row of the list of Characters: the romaji, the names and how many bounties it
	 * holds.
	 */
	public ContentSummaryResponse<CharacterNamesResponse> toSummaryResponse(ContentSummary<Character> summary,
			long bountyCount) {
		return ContentResponseMapper.toSummaryResponse(summary,
				character -> new CharacterNamesResponse(character.romaji(), character.names(), bountyCount));
	}

	/** Everything the version says, its lists in their order. */
	public CharacterResponse toResponse(Character character, CharacterLookups lookups,
			Map<UUID, NumberedReference> storyItems, ImageResponseMapper images) {
		Map<String, CharacterTranslationResponse> translations = new TreeMap<>();
		character.translations()
			.forEach((language, translation) -> translations.put(language, toResponse(translation)));
		return CharacterResponse.builder()
			.romaji(character.romaji())
			.japaneseName(character.japaneseName())
			.translations(translations)
			.status(character.status())
			.gender(character.gender())
			.birthday(character.birthday())
			.epithets(character.epithets().stream().map(entry -> toResponse(entry, storyItems)).toList())
			.aliases(character.aliases().stream().map(entry -> toResponse(entry, storyItems)).toList())
			.species(character.species().stream().map(entry -> toResponse(entry, lookups, storyItems)).toList())
			.affiliations(
					character.affiliations().stream().map(entry -> toResponse(entry, lookups, storyItems)).toList())
			.devilFruits(character.devilFruits().stream().map(entry -> toResponse(entry, lookups, storyItems)).toList())
			.relatives(character.relatives().stream().map(entry -> toResponse(entry, lookups, storyItems)).toList())
			.relativesOf(lookups.relativesOf().stream().map(entry -> toResponse(entry, storyItems)).toList())
			.image(images.toResponse(character.imageId()))
			.firstAppearance(FirstAppearanceMapper.toResponse(character.firstAppearance(), storyItems))
			.description(ParagraphResponseMapper.toResponse(character.description(), storyItems))
			.description(ParagraphResponseMapper.toResponse(character.description(), storyItems))
			.build();
	}

	private static SpeciesEntryResponse toResponse(SpeciesEntry entry, CharacterLookups lookups,
			Map<UUID, NumberedReference> storyItems) {
		return new SpeciesEntryResponse(toSpeciesResponse(entry.speciesContentId(), lookups.species()),
				discovered(entry.window(), storyItems), ended(entry.window(), storyItems));
	}

	/**
	 * The species with its names; with only its id when it has nothing approved to be
	 * called by, which the link makes impossible but a read must not fail for.
	 */
	private static SpeciesReferenceResponse toSpeciesResponse(UUID speciesContentId,
			Map<UUID, SpeciesReference> species) {
		SpeciesReference reference = species.get(speciesContentId);
		return reference == null ? new SpeciesReferenceResponse(speciesContentId, null, Map.of())
				: SpeciesResponseMapper.toResponse(reference);
	}

	/** The affiliation as of today, with its periods as written. */
	private static MembershipResponse toResponse(Membership membership, CharacterLookups lookups,
			Map<UUID, NumberedReference> storyItems) {
		return new MembershipResponse(toAffiliationResponse(membership.affiliationContentId(), lookups.affiliations()),
				membership.periods().stream().map(period -> toResponse(period, storyItems)).toList());
	}

	/** Its role sorted by language code. */
	private static MembershipPeriodResponse toResponse(MembershipPeriod period,
			Map<UUID, NumberedReference> storyItems) {
		return new MembershipPeriodResponse(discovered(period.window(), storyItems), ended(period.window(), storyItems),
				new TreeMap<>(period.role()), period.marineRank());
	}

	/**
	 * The affiliation with its names and kind; with only its id when it has nothing
	 * approved to be called by, which the link makes impossible but a read must not fail
	 * for.
	 */
	private static AffiliationReferenceResponse toAffiliationResponse(UUID affiliationContentId,
			Map<UUID, AffiliationReference> affiliations) {
		AffiliationReference reference = affiliations.get(affiliationContentId);
		return reference == null ? new AffiliationReferenceResponse(affiliationContentId, null, Map.of(), null)
				: AffiliationResponseMapper.toResponse(reference);
	}

	/** The relative as of today, with what it is to the character. */
	private static RelativeResponse toResponse(Relative relative, CharacterLookups lookups,
			Map<UUID, NumberedReference> storyItems) {
		CharacterReference reference = lookups.relatives().get(relative.relativeContentId());
		return new RelativeResponse(
				reference == null ? new CharacterReferenceResponse(relative.relativeContentId(), null, Map.of(), null)
						: toResponse(reference),
				relative.kind(), discovered(relative.window(), storyItems), ended(relative.window(), storyItems));
	}

	/**
	 * A character naming this one as a relative, with the inverse kind and its window.
	 */
	private static RelativeResponse toResponse(RelativeOf relative, Map<UUID, NumberedReference> storyItems) {
		return new RelativeResponse(toResponse(relative.character()), relative.kind(),
				discovered(relative.window(), storyItems), ended(relative.window(), storyItems));
	}

	/** The fruit as of today, with its window. */
	private static FruitUseResponse toResponse(FruitUse use, CharacterLookups lookups,
			Map<UUID, NumberedReference> storyItems) {
		return new FruitUseResponse(toDevilFruitResponse(use.devilFruitContentId(), lookups.devilFruits()),
				discovered(use.window(), storyItems), ended(use.window(), storyItems));
	}

	/**
	 * The fruit with its names; with only its id when it has nothing approved to be
	 * called by, which the link makes impossible but a read must not fail for.
	 */
	private static DevilFruitReferenceResponse toDevilFruitResponse(UUID devilFruitContentId,
			Map<UUID, DevilFruitReference> devilFruits) {
		DevilFruitReference reference = devilFruits.get(devilFruitContentId);
		return reference == null ? new DevilFruitReferenceResponse(devilFruitContentId, null, Map.of(), false)
				: toResponse(reference);
	}

	/** A Devil Fruit a character may point to. */
	public DevilFruitReferenceResponse toResponse(DevilFruitReference reference) {
		return new DevilFruitReferenceResponse(reference.id(), reference.romaji(), reference.names(),
				reference.multipleUsers());
	}

	/** A character a bounty may point to. */
	public CharacterReferenceResponse toResponse(CharacterReference reference) {
		return new CharacterReferenceResponse(reference.id(), reference.romaji(), reference.names(),
				reference.gender());
	}

	private static CharacterTranslationResponse toResponse(CharacterTranslation translation) {
		return new CharacterTranslationResponse(translation.name());
	}

	/** Sorted by language code, so the same epithet always reads the same way. */
	private static EpithetResponse toResponse(Epithet epithet, Map<UUID, NumberedReference> storyItems) {
		return new EpithetResponse(new TreeMap<>(epithet.text()), discovered(epithet.window(), storyItems),
				ended(epithet.window(), storyItems));
	}

	private static AliasResponse toResponse(Alias alias, Map<UUID, NumberedReference> storyItems) {
		return new AliasResponse(alias.name(), discovered(alias.window(), storyItems),
				ended(alias.window(), storyItems));
	}

	private static FirstAppearanceResponse discovered(StoryWindow window, Map<UUID, NumberedReference> storyItems) {
		return FirstAppearanceMapper.toResponse(window.discovered(), storyItems);
	}

	private static FirstAppearanceResponse ended(StoryWindow window, Map<UUID, NumberedReference> storyItems) {
		return FirstAppearanceMapper.toResponse(window.ended(), storyItems);
	}

}
