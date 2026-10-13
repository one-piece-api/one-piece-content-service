package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.ArcVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentDefinition;
import dev.onepieceapi.contentservice.service.exception.ArcNotFoundException;
import dev.onepieceapi.contentservice.service.validation.ArcValidator;
import dev.onepieceapi.exception.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.UUID;

/** The arc, as the generic workflow sees it. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
class ArcDefinition implements ContentDefinition<Arc, ArcVersionEntity> {

	private final ArcVersionRepository repository;

	private final ArcValidator validator;

	private final ArcRules rules;

	@Override
	public EntityType entityType() {
		return EntityType.ARC;
	}

	@Override
	public Version<Arc> toDomain(ArcVersionEntity entity) {
		return StoryVersionMapper.toDomain(entity);
	}

	@Override
	public ArcVersionEntity newVersion(ContentVersionEntity workflow) {
		return new ArcVersionEntity(workflow);
	}

	@Override
	public void rewrite(ArcVersionEntity entity, Arc body, Instant now) {
		StoryVersionMapper.rewrite(entity, body, now);
	}

	@Override
	public NotFoundException notFound(UUID contentId) {
		return new ArcNotFoundException(contentId);
	}

}
