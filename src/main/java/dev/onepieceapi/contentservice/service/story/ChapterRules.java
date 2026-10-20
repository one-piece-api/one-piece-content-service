package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import dev.onepieceapi.contentservice.service.affiliation.AffiliationRelations;
import dev.onepieceapi.contentservice.service.bounty.BountyRelations;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.LinkedItems;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitRelations;
import dev.onepieceapi.contentservice.service.devilfruittype.DevilFruitTypeRelations;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * What a chapter needs of its arc and of its volume, and owes what first appears in it:
 * the rules of every relation ({@link RelationRules}) for
 * {@link StoryRelations#CHAPTER_ARC} and {@link StoryRelations#CHAPTER_VOLUME} as the
 * pointing side - a chapter goes online only while its arc is, and its volume when it has
 * one, holding both shared first - and for the first appearances as the target (ADR-0008)
 * - a chapter cannot be retired while fruits, or types (by themselves or through a
 * subcategory), are online first appearing in it, holding it exclusively first. Nothing
 * of its own on top.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class ChapterRules implements ContentRules<Chapter> {

	private final ContentRepository contentRepository;

	private final ArcVersionRepository arcRepository;

	private final VolumeVersionRepository volumeRepository;

	private final DevilFruitVersionRepository fruitRepository;

	private final DevilFruitTypeVersionRepository typeRepository;

	private final BountyVersionRepository bountyRepository;

	private final AffiliationVersionRepository affiliationRepository;

	private final RulesProperties properties;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<Chapter> version) {
		RelationRules.lockTargetBefore(action, StoryRelations.CHAPTER_ARC, version.body(), this.contentRepository);
		RelationRules.lockTargetBefore(action, StoryRelations.CHAPTER_VOLUME, version.body(), this.contentRepository);
		RelationRules.lockLinkedBefore(action, contentId, this.contentRepository);
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<Chapter> version) {
		return RelationRules.targetNotOnline(action, StoryRelations.CHAPTER_ARC, version.body(), this.arcRepository)
			.or(() -> RelationRules.targetNotOnline(action, StoryRelations.CHAPTER_VOLUME, version.body(),
					this.volumeRepository))
			.or(() -> fruitsLinked(action, contentId))
			.or(() -> typesLinked(action, contentId))
			.or(() -> bountiesLinked(action, contentId))
			.or(() -> affiliationsLinked(action, contentId));
	}

	private Optional<ActionBlock> fruitsLinked(VersionAction action, UUID contentId) {
		var fruits = new LinkedItems(this.fruitRepository::countOnlineFirstAppearingIn,
				this.fruitRepository::findOnlineFirstAppearingIn);
		return RelationRules.onlineItemsLinked(action, DevilFruitRelations.FIRST_APPEARANCE_CHAPTER, contentId, fruits,
				this.properties.blockingPreviewSize());
	}

	private Optional<ActionBlock> typesLinked(VersionAction action, UUID contentId) {
		var types = new LinkedItems(this.typeRepository::countOnlineFirstAppearingIn,
				this.typeRepository::findOnlineFirstAppearingIn);
		return RelationRules.onlineItemsLinked(action, DevilFruitTypeRelations.FIRST_APPEARANCE_CHAPTER, contentId,
				types, this.properties.blockingPreviewSize());
	}

	private Optional<ActionBlock> bountiesLinked(VersionAction action, UUID contentId) {
		var bounties = new LinkedItems(this.bountyRepository::countOnlineFirstAppearingIn,
				this.bountyRepository::findOnlineFirstAppearingIn);
		return RelationRules.onlineItemsLinked(action, BountyRelations.FIRST_APPEARANCE_CHAPTER, contentId, bounties,
				this.properties.blockingPreviewSize());
	}

	private Optional<ActionBlock> affiliationsLinked(VersionAction action, UUID contentId) {
		var affiliations = new LinkedItems(this.affiliationRepository::countOnlineFirstAppearingIn,
				this.affiliationRepository::findOnlineFirstAppearingIn);
		return RelationRules.onlineItemsLinked(action, AffiliationRelations.FIRST_APPEARANCE_CHAPTER, contentId,
				affiliations, this.properties.blockingPreviewSize());
	}

}
