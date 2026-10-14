package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.story.Volume;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.LinkedItems;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * What a volume owes its chapters: the rules of every relation ({@link RelationRules}) as
 * the target of {@link StoryRelations#CHAPTER_VOLUME} - a volume cannot be retired while
 * chapters are online in it. Nothing of its own on top.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class VolumeRules implements ContentRules<Volume> {

	private final ContentRepository contentRepository;

	private final ChapterVersionRepository chapterRepository;

	private final RulesProperties properties;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<Volume> version) {
		RelationRules.lockLinkedBefore(action, contentId, this.contentRepository);
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<Volume> version) {
		var chapters = new LinkedItems(this.chapterRepository::countOnlineInVolume,
				this.chapterRepository::findOnlineInVolume);
		return RelationRules.onlineItemsLinked(action, StoryRelations.CHAPTER_VOLUME, contentId, chapters,
				this.properties.blockingPreviewSize());
	}

}
