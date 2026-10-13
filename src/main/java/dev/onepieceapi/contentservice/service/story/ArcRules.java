package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * What an arc needs of its saga: the rules of every relation ({@link RelationRules}) for
 * {@link StoryRelations#ARC_SAGA} - an arc goes online only while its saga is, holding
 * the saga shared first so a retirement of it waits. Nothing of its own on top.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class ArcRules implements ContentRules<Arc> {

	private final ContentRepository contentRepository;

	private final SagaVersionRepository sagaRepository;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<Arc> version) {
		RelationRules.lockTargetBefore(action, StoryRelations.ARC_SAGA, version.body(), this.contentRepository);
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<Arc> version) {
		return RelationRules.targetNotOnline(action, StoryRelations.ARC_SAGA, version.body(), this.sagaRepository);
	}

}
