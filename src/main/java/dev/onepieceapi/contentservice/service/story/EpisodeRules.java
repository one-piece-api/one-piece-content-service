package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
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
 * What an episode needs of its arc, when it has one, and owes what first appears in it:
 * the rules of every relation ({@link RelationRules}) for
 * {@link StoryRelations#EPISODE_ARC} as the pointing side - an episode goes online only
 * while its arc is, holding the arc shared first - and for the first appearances as the
 * target (ADR-0008) - an episode cannot be retired while fruits, or types (by themselves
 * or through a subcategory), are online first appearing in it, holding it exclusively
 * first. Nothing of its own on top.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class EpisodeRules implements ContentRules<Episode> {

	private final ContentRepository contentRepository;

	private final ArcVersionRepository arcRepository;

	private final DevilFruitVersionRepository fruitRepository;

	private final DevilFruitTypeVersionRepository typeRepository;

	private final BountyVersionRepository bountyRepository;

	private final RulesProperties properties;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<Episode> version) {
		RelationRules.lockTargetBefore(action, StoryRelations.EPISODE_ARC, version.body(), this.contentRepository);
		RelationRules.lockLinkedBefore(action, contentId, this.contentRepository);
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<Episode> version) {
		return RelationRules.targetNotOnline(action, StoryRelations.EPISODE_ARC, version.body(), this.arcRepository)
			.or(() -> fruitsLinked(action, contentId))
			.or(() -> typesLinked(action, contentId))
			.or(() -> bountiesLinked(action, contentId));
	}

	private Optional<ActionBlock> fruitsLinked(VersionAction action, UUID contentId) {
		var fruits = new LinkedItems(this.fruitRepository::countOnlineFirstAppearingIn,
				this.fruitRepository::findOnlineFirstAppearingIn);
		return RelationRules.onlineItemsLinked(action, DevilFruitRelations.FIRST_APPEARANCE_EPISODE, contentId, fruits,
				this.properties.blockingPreviewSize());
	}

	private Optional<ActionBlock> typesLinked(VersionAction action, UUID contentId) {
		var types = new LinkedItems(this.typeRepository::countOnlineFirstAppearingIn,
				this.typeRepository::findOnlineFirstAppearingIn);
		return RelationRules.onlineItemsLinked(action, DevilFruitTypeRelations.FIRST_APPEARANCE_EPISODE, contentId,
				types, this.properties.blockingPreviewSize());
	}

	private Optional<ActionBlock> bountiesLinked(VersionAction action, UUID contentId) {
		var bounties = new LinkedItems(this.bountyRepository::countOnlineFirstAppearingIn,
				this.bountyRepository::findOnlineFirstAppearingIn);
		return RelationRules.onlineItemsLinked(action, BountyRelations.FIRST_APPEARANCE_EPISODE, contentId, bounties,
				this.properties.blockingPreviewSize());
	}

}
