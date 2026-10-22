package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.FruitUse;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.AffiliationVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.LinkedItems;
import dev.onepieceapi.contentservice.service.bounty.BountyRelations;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * What a Character needs to go online - published or restored (implementation plan of the
 * Character, D8, D9, D10, D12): a first appearance in a chapter or an episode, each one
 * it names online, every species, affiliation and Devil Fruit of its lists online, a rank
 * only in an affiliation whose online version is a Navy one, and a current Devil Fruit
 * that no other character online has as a current one - unless the fruit has several
 * users. Not even {@code content:admin} lifts it. Its retirement is refused while online
 * bounties name it as their holder (C6).
 * <p>
 * The chapter, the episode, every species and every affiliation are held shared first, so
 * the retirement of any of them - or an affiliation going online as another kind - waits
 * for a character going online with it ({@code ChapterRules}, {@code EpisodeRules},
 * {@code SpeciesRules}, {@code AffiliationRules}). The Devil Fruits are held in the order
 * of their ids, a current one exclusively and a former one shared: two characters going
 * online with the same current fruit never check it together, and a fruit going online or
 * being retired waits for them too ({@code DevilFruitRules}).
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

	private final DevilFruitVersionRepository devilFruitRepository;

	private final CharacterVersionRepository characterRepository;

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
		lockDevilFruitsBefore(action, character);
	}

	/**
	 * A single pass in the order of the ids, so two characters sharing fruits never wait
	 * for each other in turns.
	 */
	private void lockDevilFruitsBefore(VersionAction action, Character character) {
		if (!RelationRules.GOING_ONLINE.contains(action)) {
			return;
		}
		character.devilFruits().stream().sorted(Comparator.comparing(FruitUse::devilFruitContentId)).forEach(use -> {
			if (use.former()) {
				this.contentRepository.lockShared(use.devilFruitContentId());
			}
			else {
				this.contentRepository.lockExclusive(use.devilFruitContentId());
			}
		});
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
			.or(() -> RelationRules.targetNotOnline(action, CharacterRelations.DEVIL_FRUIT, character.devilFruits(),
					CharacterRelations.DEVIL_FRUITS_FIELD, this.devilFruitRepository))
			.or(() -> rankNotAllowed(action, character))
			.or(() -> devilFruitInUse(action, contentId, character))
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

	/**
	 * Going online, the first current fruit that has one user at a time - as its online
	 * version says - and that another character online has as a current one. Every fruit
	 * is online by now: the check before the rank.
	 */
	private Optional<ActionBlock> devilFruitInUse(VersionAction action, UUID contentId, Character character) {
		if (!RelationRules.GOING_ONLINE.contains(action)) {
			return Optional.empty();
		}
		List<FruitUse> fruits = character.devilFruits();
		return IntStream.range(0, fruits.size())
			.filter(index -> !fruits.get(index).former())
			.mapToObj(index -> inUseByOthers(index, fruits.get(index).devilFruitContentId(), contentId))
			.flatMap(Optional::stream)
			.findFirst();
	}

	private Optional<ActionBlock> inUseByOthers(int index, UUID devilFruitContentId, UUID contentId) {
		return this.devilFruitRepository.findMostRecent(List.of(devilFruitContentId), Set.of(VersionStatus.PUBLISHED))
			.stream()
			.findFirst()
			.filter(online -> !onlineFruit(online).multipleUsers())
			.flatMap(online -> {
				var users = this.characterRepository.findOnlineOtherCurrentUsersOf(devilFruitContentId, contentId,
						PageRequest.ofSize(this.properties.blockingPreviewSize()));
				if (users.isEmpty()) {
					return Optional.empty();
				}
				Map<String, Object> detail = new LinkedHashMap<>();
				detail.put(CharacterRelations.DEVIL_FRUITS_FIELD + "Index", index);
				detail.put("devilFruitId", devilFruitContentId);
				detail.put("devilFruitRomaji", online.getRomaji());
				detail.put(CharacterRelations.ITEMS_NAME, users.stream().map(RelationRules::named).toList());
				return Optional.of(new ActionBlock(BlockReason.DEVIL_FRUIT_IN_USE, detail));
			});
	}

	private static DevilFruit onlineFruit(DevilFruitVersionEntity version) {
		return DevilFruitVersionMapper.toDomain(version).body();
	}

	/** The queries of the bounties for the character they name as holder. */
	private LinkedItems holdingBounties() {
		return new LinkedItems(this.bountyRepository::countOnlineWithHolder,
				this.bountyRepository::findOnlineWithHolder);
	}

}
