package dev.onepieceapi.contentservice.service.bounty;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.LinkedItems;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What a bounty needs to go online and to be retired (implementation plan of the Bounty,
 * B2): the rules of every relation ({@link RelationRules}) for the bounty it replaces and
 * for its first appearance, and, on top, the two of the chain - at most one online bounty
 * replaces a bounty, and no cycle through the online ones. Not even {@code content:admin}
 * lifts any of them.
 * <p>
 * Every change to the online chain - publish, restore, retire - first takes one lock
 * ({@link ContentRepository#lockBountyChain()}), so two of them never pass the checks
 * together: two drafts both naming the same previous would otherwise both read "free".
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class BountyRules implements ContentRules<Bounty> {

	private static final Set<VersionAction> CHANGING_THE_CHAIN = Set.of(VersionAction.PUBLISH, VersionAction.RESTORE,
			VersionAction.RETIRE);

	private static final Set<VersionStatus> ONLINE = Set.of(VersionStatus.PUBLISHED);

	private final ContentRepository contentRepository;

	private final BountyVersionRepository bountyRepository;

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	private final BountyChains chains;

	private final RulesProperties properties;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<Bounty> version) {
		if (CHANGING_THE_CHAIN.contains(action)) {
			this.contentRepository.lockBountyChain();
		}
		RelationRules.lockTargetBefore(action, BountyRelations.PREVIOUS, version.body(), this.contentRepository);
		RelationRules.lockTargetBefore(action, BountyRelations.FIRST_APPEARANCE_CHAPTER, version.body(),
				this.contentRepository);
		RelationRules.lockTargetBefore(action, BountyRelations.FIRST_APPEARANCE_EPISODE, version.body(),
				this.contentRepository);
		RelationRules.lockLinkedBefore(action, contentId, this.contentRepository);
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<Bounty> version) {
		Bounty bounty = version.body();
		return FirstAppearanceRules.missing(action, bounty)
			.or(() -> RelationRules.targetNotOnline(action, BountyRelations.PREVIOUS, bounty, this.bountyRepository))
			.or(() -> RelationRules.targetNotOnline(action, BountyRelations.FIRST_APPEARANCE_CHAPTER, bounty,
					this.chapterRepository))
			.or(() -> RelationRules.targetNotOnline(action, BountyRelations.FIRST_APPEARANCE_EPISODE, bounty,
					this.episodeRepository))
			.or(() -> alreadyReplaced(action, contentId, bounty))
			.or(() -> closesCycle(action, contentId, bounty))
			.or(() -> replacedBy(action, contentId));
	}

	/** Another bounty online already replaces the previous one. */
	private Optional<ActionBlock> alreadyReplaced(VersionAction action, UUID contentId, Bounty bounty) {
		UUID previous = bounty.previousContentId();
		if (!RelationRules.GOING_ONLINE.contains(action) || previous == null) {
			return Optional.empty();
		}
		return this.bountyRepository.findOnlineOthersReplacing(previous, contentId, PageRequest.ofSize(1))
			.stream()
			.findFirst()
			.map(other -> {
				Map<String, Object> detail = new LinkedHashMap<>();
				detail.put("previousId", previous);
				detail.put("replacedBy", RelationRules.named(other));
				return new ActionBlock(BlockReason.PREVIOUS_ALREADY_REPLACED, detail);
			});
	}

	/** Following the previous of the online bounties comes back to this one. */
	private Optional<ActionBlock> closesCycle(VersionAction action, UUID contentId, Bounty bounty) {
		UUID previous = bounty.previousContentId();
		if (!RelationRules.GOING_ONLINE.contains(action) || previous == null
				|| !this.chains.reaches(previous, contentId, ONLINE)) {
			return Optional.empty();
		}
		return Optional.of(new ActionBlock(BlockReason.BOUNTY_CHAIN_CYCLE, Map.of("previousId", previous)));
	}

	/** Retiring: online bounties still name this one as the one they replace. */
	private Optional<ActionBlock> replacedBy(VersionAction action, UUID contentId) {
		var replacing = new LinkedItems(this.bountyRepository::countOnlineReplacing,
				this.bountyRepository::findOnlineReplacing);
		return RelationRules.onlineItemsLinked(action, BountyRelations.PREVIOUS, contentId, replacing,
				this.properties.blockingPreviewSize());
	}

}
