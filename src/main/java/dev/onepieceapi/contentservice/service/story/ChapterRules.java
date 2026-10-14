package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * What a chapter needs of its arc and of its volume: the rules of every relation
 * ({@link RelationRules}) for {@link StoryRelations#CHAPTER_ARC} and
 * {@link StoryRelations#CHAPTER_VOLUME} - a chapter goes online only while its arc is,
 * and its volume when it has one, holding both shared first. Nothing of its own on top.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class ChapterRules implements ContentRules<Chapter> {

	private final ContentRepository contentRepository;

	private final ArcVersionRepository arcRepository;

	private final VolumeVersionRepository volumeRepository;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<Chapter> version) {
		RelationRules.lockTargetBefore(action, StoryRelations.CHAPTER_ARC, version.body(), this.contentRepository);
		RelationRules.lockTargetBefore(action, StoryRelations.CHAPTER_VOLUME, version.body(), this.contentRepository);
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<Chapter> version) {
		return RelationRules.targetNotOnline(action, StoryRelations.CHAPTER_ARC, version.body(), this.arcRepository)
			.or(() -> RelationRules.targetNotOnline(action, StoryRelations.CHAPTER_VOLUME, version.body(),
					this.volumeRepository));
	}

}
