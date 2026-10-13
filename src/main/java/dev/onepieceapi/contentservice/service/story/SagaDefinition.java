package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.SagaVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentDefinition;
import dev.onepieceapi.contentservice.service.exception.SagaNotFoundException;
import dev.onepieceapi.contentservice.service.validation.SagaValidator;
import dev.onepieceapi.exception.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.UUID;

/** The saga, as the generic workflow sees it. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
class SagaDefinition implements ContentDefinition<Saga, SagaVersionEntity> {

	private final SagaVersionRepository repository;

	private final SagaValidator validator;

	private final SagaRules rules;

	@Override
	public EntityType entityType() {
		return EntityType.SAGA;
	}

	@Override
	public Version<Saga> toDomain(SagaVersionEntity entity) {
		return StoryVersionMapper.toDomain(entity);
	}

	@Override
	public SagaVersionEntity newVersion(ContentVersionEntity workflow) {
		return new SagaVersionEntity(workflow);
	}

	@Override
	public void rewrite(SagaVersionEntity entity, Saga body, Instant now) {
		StoryVersionMapper.rewrite(entity, body, now);
	}

	@Override
	public NotFoundException notFound(UUID contentId) {
		return new SagaNotFoundException(contentId);
	}

}
