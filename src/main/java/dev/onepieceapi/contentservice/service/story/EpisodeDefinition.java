package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.EpisodeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentDefinition;
import dev.onepieceapi.contentservice.service.exception.EpisodeNotFoundException;
import dev.onepieceapi.contentservice.service.validation.EpisodeValidator;
import dev.onepieceapi.exception.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.UUID;

/** The episode, as the generic workflow sees it. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
class EpisodeDefinition implements ContentDefinition<Episode, EpisodeVersionEntity> {

	private final EpisodeVersionRepository repository;

	private final EpisodeValidator validator;

	private final EpisodeRules rules;

	@Override
	public EntityType entityType() {
		return EntityType.EPISODE;
	}

	@Override
	public Version<Episode> toDomain(EpisodeVersionEntity entity) {
		return StoryVersionMapper.toDomain(entity);
	}

	@Override
	public EpisodeVersionEntity newVersion(ContentVersionEntity workflow) {
		return new EpisodeVersionEntity(workflow);
	}

	@Override
	public void rewrite(EpisodeVersionEntity entity, Episode body, Instant now) {
		StoryVersionMapper.rewrite(entity, body, now);
	}

	@Override
	public NotFoundException notFound(UUID contentId) {
		return new EpisodeNotFoundException(contentId);
	}

}
