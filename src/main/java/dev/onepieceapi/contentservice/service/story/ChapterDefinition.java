package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.ChapterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentDefinition;
import dev.onepieceapi.contentservice.service.exception.ChapterNotFoundException;
import dev.onepieceapi.contentservice.service.validation.ChapterValidator;
import dev.onepieceapi.exception.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.UUID;

/** The chapter, as the generic workflow sees it. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
class ChapterDefinition implements ContentDefinition<Chapter, ChapterVersionEntity> {

	private final ChapterVersionRepository repository;

	private final ChapterValidator validator;

	private final ChapterRules rules;

	@Override
	public EntityType entityType() {
		return EntityType.CHAPTER;
	}

	@Override
	public Version<Chapter> toDomain(ChapterVersionEntity entity) {
		return StoryVersionMapper.toDomain(entity);
	}

	@Override
	public ChapterVersionEntity newVersion(ContentVersionEntity workflow) {
		return new ChapterVersionEntity(workflow);
	}

	@Override
	public void rewrite(ChapterVersionEntity entity, Chapter body, Instant now) {
		StoryVersionMapper.rewrite(entity, body, now);
	}

	@Override
	public NotFoundException notFound(UUID contentId) {
		return new ChapterNotFoundException(contentId);
	}

}
