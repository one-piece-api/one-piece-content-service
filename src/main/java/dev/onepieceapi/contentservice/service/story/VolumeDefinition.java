package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Volume;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.VolumeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentDefinition;
import dev.onepieceapi.contentservice.service.exception.VolumeNotFoundException;
import dev.onepieceapi.contentservice.service.validation.VolumeValidator;
import dev.onepieceapi.exception.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.UUID;

/** The volume, as the generic workflow sees it. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
@Component
class VolumeDefinition implements ContentDefinition<Volume, VolumeVersionEntity> {

	private final VolumeVersionRepository repository;

	private final VolumeValidator validator;

	private final VolumeRules rules;

	@Override
	public EntityType entityType() {
		return EntityType.VOLUME;
	}

	@Override
	public Version<Volume> toDomain(VolumeVersionEntity entity) {
		return StoryVersionMapper.toDomain(entity);
	}

	@Override
	public VolumeVersionEntity newVersion(ContentVersionEntity workflow) {
		return new VolumeVersionEntity(workflow);
	}

	@Override
	public void rewrite(VolumeVersionEntity entity, Volume body, Instant now) {
		StoryVersionMapper.rewrite(entity, body, now);
	}

	@Override
	public NotFoundException notFound(UUID contentId) {
		return new VolumeNotFoundException(contentId);
	}

}
