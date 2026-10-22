package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.mapper.AffiliationVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.LinkedItems;
import dev.onepieceapi.contentservice.service.bounty.BountyRelations;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * What a Character needs to go online - published or restored (implementation plan of the
 * Character, D8, D9, D10): a first appearance in a chapter or an episode, each one it
 * names online, every species and every affiliation of its lists online, and a rank only
 * in an affiliation whose online version is a Navy one. Not even {@code content:admin}
 * lifts it. Its retirement is refused while online bounties name it as their holder (C6).
 * <p>
 * The chapter, the episode, every species and every affiliation are held shared first, so
 * the retirement of any of them - or an affiliation going online as another kind - waits
 * for a character going online with it ({@code ChapterRules}, {@code EpisodeRules},
 * {@code SpeciesRules}, {@code AffiliationRules}).
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class CharacterRules implements ContentRules<Character> {

	private final ContentRepository contentRepository;

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	private final SpeciesVersionRepository speciesRepository;

	private final AffiliationVersionRepository affiliationRepository;

	private final BountyVersionRepository bountyRepository;

	private final RulesProperties properties;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<Character> version) {
		RelationRules.lockLinkedBefore(action, contentId, this.contentRepository);
		Character character = version.body();
		RelationRules.lockTargetBefore(action, CharacterRelations.FIRST_APPEARANCE_CHAPTER, character,
				this.contentRepository);
		RelationRules.lockTargetBefore(action, CharacterRelations.FIRST_APPEARANCE_EPISODE, character,
				this.contentRepository);
		RelationRules.lockTargetBefore(action, CharacterRelations.SPECIES, character.speciesIds(),
				this.contentRepository);
		RelationRules.lockTargetBefore(action, CharacterRelations.AFFILIATION, character.affiliations(),
				this.contentRepository);
	}

	/** First what the character itself lacks, then what it points to, in its order. */
	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<Character> version) {
		Character character = version.body();
		return FirstAppearanceRules.missing(action, character)
			.or(() -> RelationRules.targetNotOnline(action, CharacterRelations.FIRST_APPEARANCE_CHAPTER, character,
					this.chapterRepository))
			.or(() -> RelationRules.targetNotOnline(action, CharacterRelations.FIRST_APPEARANCE_EPISODE, character,
					this.episodeRepository))
			.or(() -> RelationRules.targetNotOnline(action, CharacterRelations.SPECIES, character.speciesIds(),
					CharacterRelations.SPECIES_FIELD, this.speciesRepository))
			.or(() -> RelationRules.targetNotOnline(action, CharacterRelations.AFFILIATION, character.affiliations(),
					CharacterRelations.AFFILIATIONS_FIELD, this.affiliationRepository))
			.or(() -> rankNotAllowed(action, character))
			.or(() -> RelationRules.onlineItemsLinked(action, BountyRelations.HOLDER, contentId, holdingBounties(),
					this.properties.blockingPreviewSize()));
	}

	/**
	 * Going online, the first rank held in an affiliation whose online version is not a
	 * Navy one - the save checked the latest approved one, which may have changed since.
	 * Every affiliation is online by now: the check before this one.
	 */
	private Optional<ActionBlock> rankNotAllowed(VersionAction action, Character character) {
		if (!RelationRules.GOING_ONLINE.contains(action)) {
			return Optional.empty();
		}
		List<Membership> memberships = character.affiliations();
		return IntStream.range(0, memberships.size())
			.filter(index -> memberships.get(index).marineRank() != null)
			.mapToObj(index -> notNavyOnline(index, memberships.get(index).affiliationContentId()))
			.flatMap(Optional::stream)
			.findFirst();
	}

	private Optional<ActionBlock> notNavyOnline(int index, UUID affiliationContentId) {
		return this.affiliationRepository.findMostRecent(List.of(affiliationContentId), Set.of(VersionStatus.PUBLISHED))
			.stream()
			.findFirst()
			.filter(online -> AffiliationVersionMapper.toDomain(online).body().kind() != AffiliationKind.NAVY)
			.map(online -> {
				Map<String, Object> detail = new LinkedHashMap<>();
				detail.put(CharacterRelations.AFFILIATIONS_FIELD + "Index", index);
				detail.put("affiliationId", affiliationContentId);
				detail.put("affiliationRomaji", online.getRomaji());
				return new ActionBlock(BlockReason.MARINE_RANK_NOT_ALLOWED, detail);
			});
	}

	/** The queries of the bounties for the character they name as holder. */
	private LinkedItems holdingBounties() {
		return new LinkedItems(this.bountyRepository::countOnlineWithHolder,
				this.bountyRepository::findOnlineWithHolder);
	}

}
