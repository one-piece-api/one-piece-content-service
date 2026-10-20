package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.persistence.entity.EpisodeVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * The episodes as the first appearances point to them (ADR-0008), and those a first
 * appearance may name.
 */
@Service
public class EpisodeLinks extends NumberedLinks<Episode, EpisodeVersionEntity> {

	@Autowired
	public EpisodeLinks(EpisodeVersionRepository repository, Clock clock) {
		super(repository, StoryVersionMapper::toDomain, clock);
	}

}
