package dev.onepieceapi.contentservice.service.affiliation;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationKind;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.CharacterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.service.character.CharacterRelations;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.LinkedItems;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What an Affiliation needs to go online - published or restored (implementation plan of
 * the Affiliation, D4): a first appearance in a chapter or an episode, and each one it
 * names online. And what it owes its characters (implementation plan of the Character,
 * D10): it cannot be retired while online characters name it, nor go online as another
 * kind than the Navy while online characters hold a rank in it. Not even
 * {@code content:admin} lifts them.
 * <p>
 * The chapter and the episode are held first, so the retirement of either waits for an
 * affiliation going online with it ({@code ChapterRules}, {@code EpisodeRules}); the
 * affiliation is held exclusively before it is retired or goes online, so it waits for
 * any character going online with it ({@code CharacterRules}).
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class AffiliationRules implements ContentRules<Affiliation> {

	private final ContentRepository contentRepository;

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	private final CharacterVersionRepository characterRepository;

	private final RulesProperties properties;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<Affiliation> version) {
		RelationRules.lockLinkedBefore(action, contentId, this.contentRepository);
		if (RelationRules.GOING_ONLINE.contains(action)) {
			this.contentRepository.lockExclusive(contentId);
		}
		RelationRules.lockTargetBefore(action, AffiliationRelations.FIRST_APPEARANCE_CHAPTER, version.body(),
				this.contentRepository);
		RelationRules.lockTargetBefore(action, AffiliationRelations.FIRST_APPEARANCE_EPISODE, version.body(),
				this.contentRepository);
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<Affiliation> version) {
		Affiliation affiliation = version.body();
		return FirstAppearanceRules.missing(action, affiliation)
			.or(() -> RelationRules.targetNotOnline(action, AffiliationRelations.FIRST_APPEARANCE_CHAPTER, affiliation,
					this.chapterRepository))
			.or(() -> RelationRules.targetNotOnline(action, AffiliationRelations.FIRST_APPEARANCE_EPISODE, affiliation,
					this.episodeRepository))
			.or(() -> rankInUse(action, contentId, affiliation))
			.or(() -> RelationRules.onlineItemsLinked(action, CharacterRelations.AFFILIATION, contentId,
					linkedCharacters(), this.properties.blockingPreviewSize()));
	}

	/**
	 * Going online as another kind than the Navy, while online characters hold a rank in
	 * the affiliation: how many, and the first few by romaji.
	 */
	private Optional<ActionBlock> rankInUse(VersionAction action, UUID contentId, Affiliation affiliation) {
		if (!RelationRules.GOING_ONLINE.contains(action) || affiliation.kind() == AffiliationKind.NAVY) {
			return Optional.empty();
		}
		long count = this.characterRepository.countOnlineWithRankIn(contentId);
		if (count == 0) {
			return Optional.empty();
		}
		List<Map<String, Object>> named = this.characterRepository
			.findOnlineWithRankIn(contentId, PageRequest.ofSize(this.properties.blockingPreviewSize()))
			.stream()
			.map(RelationRules::named)
			.toList();
		return Optional.of(new ActionBlock(BlockReason.MARINE_RANK_IN_USE,
				Map.of("count", count, CharacterRelations.ITEMS_NAME, named)));
	}

	/** The queries of the characters for the affiliation they name. */
	private LinkedItems linkedCharacters() {
		return new LinkedItems(this.characterRepository::countOnlineWithAffiliation,
				this.characterRepository::findOnlineWithAffiliation);
	}

}
