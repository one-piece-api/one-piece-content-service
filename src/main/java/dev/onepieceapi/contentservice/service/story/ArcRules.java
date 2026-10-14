package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.LinkedItems;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * What an arc needs of its saga and owes its chapters and episodes: the rules of every
 * relation ({@link RelationRules}) for {@link StoryRelations#ARC_SAGA} as the pointing
 * side - an arc goes online only while its saga is, holding the saga shared first so a
 * retirement of it waits - and for {@link StoryRelations#CHAPTER_ARC} and
 * {@link StoryRelations#EPISODE_ARC} as the target - an arc cannot be retired while
 * chapters or episodes are online in it. Nothing of its own on top.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class ArcRules implements ContentRules<Arc> {

	private final ContentRepository contentRepository;

	private final SagaVersionRepository sagaRepository;

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	private final RulesProperties properties;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<Arc> version) {
		RelationRules.lockTargetBefore(action, StoryRelations.ARC_SAGA, version.body(), this.contentRepository);
		RelationRules.lockLinkedBefore(action, contentId, this.contentRepository);
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<Arc> version) {
		return RelationRules.targetNotOnline(action, StoryRelations.ARC_SAGA, version.body(), this.sagaRepository)
			.or(() -> chaptersLinked(action, contentId))
			.or(() -> episodesLinked(action, contentId));
	}

	private Optional<ActionBlock> chaptersLinked(VersionAction action, UUID contentId) {
		var chapters = new LinkedItems(this.chapterRepository::countOnlineInArc,
				this.chapterRepository::findOnlineInArc);
		return RelationRules.onlineItemsLinked(action, StoryRelations.CHAPTER_ARC, contentId, chapters,
				this.properties.blockingPreviewSize());
	}

	private Optional<ActionBlock> episodesLinked(VersionAction action, UUID contentId) {
		var episodes = new LinkedItems(this.episodeRepository::countOnlineInArc,
				this.episodeRepository::findOnlineInArc);
		return RelationRules.onlineItemsLinked(action, StoryRelations.EPISODE_ARC, contentId, episodes,
				this.properties.blockingPreviewSize());
	}

}
