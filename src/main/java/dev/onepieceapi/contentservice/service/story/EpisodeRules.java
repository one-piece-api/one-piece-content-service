package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * What an episode needs of its arc, when it has one: the rules of every relation
 * ({@link RelationRules}) for {@link StoryRelations#EPISODE_ARC} - an episode goes online
 * only while its arc is, holding the arc shared first. Nothing of its own on top.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class EpisodeRules implements ContentRules<Episode> {

	private final ContentRepository contentRepository;

	private final ArcVersionRepository arcRepository;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<Episode> version) {
		RelationRules.lockTargetBefore(action, StoryRelations.EPISODE_ARC, version.body(), this.contentRepository);
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<Episode> version) {
		return RelationRules.targetNotOnline(action, StoryRelations.EPISODE_ARC, version.body(), this.arcRepository);
	}

}
