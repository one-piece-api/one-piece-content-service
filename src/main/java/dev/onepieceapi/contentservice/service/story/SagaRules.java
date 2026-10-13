package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
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
 * What a saga owes its arcs: the rules of every relation ({@link RelationRules}) as the
 * target of {@link StoryRelations#ARC_SAGA} - a saga cannot be retired while arcs are
 * online in it. Nothing of its own on top.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class SagaRules implements ContentRules<Saga> {

	private final ContentRepository contentRepository;

	private final ArcVersionRepository arcRepository;

	private final RulesProperties properties;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<Saga> version) {
		RelationRules.lockLinkedBefore(action, contentId, this.contentRepository);
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<Saga> version) {
		var linkedArcs = new LinkedItems(this.arcRepository::countOnlineLinkedTo,
				this.arcRepository::findOnlineLinkedTo);
		return RelationRules.onlineItemsLinked(action, StoryRelations.ARC_SAGA, contentId, linkedArcs,
				this.properties.blockingPreviewSize());
	}

}
