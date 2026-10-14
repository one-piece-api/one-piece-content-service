package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.story.Volume;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.persistence.entity.ArcVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ChapterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.EpisodeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.SagaVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.VolumeVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentTitleSource;
import dev.onepieceapi.contentservice.service.content.VersionBodyTitleSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A story content is called by its title in each language, or else by its number. */
@Configuration(proxyBeanMethods = false)
class StoryTitleSources {

	@Bean
	ContentTitleSource sagaTitleSource(SagaVersionRepository repository) {
		return new VersionBodyTitleSource<Saga, SagaVersionEntity>(EntityType.SAGA, repository,
				StoryVersionMapper::toDomain);
	}

	@Bean
	ContentTitleSource arcTitleSource(ArcVersionRepository repository) {
		return new VersionBodyTitleSource<Arc, ArcVersionEntity>(EntityType.ARC, repository,
				StoryVersionMapper::toDomain);
	}

	@Bean
	ContentTitleSource volumeTitleSource(VolumeVersionRepository repository) {
		return new VersionBodyTitleSource<Volume, VolumeVersionEntity>(EntityType.VOLUME, repository,
				StoryVersionMapper::toDomain);
	}

	@Bean
	ContentTitleSource chapterTitleSource(ChapterVersionRepository repository) {
		return new VersionBodyTitleSource<Chapter, ChapterVersionEntity>(EntityType.CHAPTER, repository,
				StoryVersionMapper::toDomain);
	}

	@Bean
	ContentTitleSource episodeTitleSource(EpisodeVersionRepository repository) {
		return new VersionBodyTitleSource<Episode, EpisodeVersionEntity>(EntityType.EPISODE, repository,
				StoryVersionMapper::toDomain);
	}

}
