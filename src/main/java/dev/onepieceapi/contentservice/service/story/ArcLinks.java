package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.persistence.entity.ArcVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * The arcs as the chapters and episodes point to them, and those they may be linked to.
 */
@Service
public class ArcLinks extends NumberedLinks<Arc, ArcVersionEntity> {

	@Autowired
	public ArcLinks(ArcVersionRepository repository, Clock clock) {
		super(repository, StoryVersionMapper::toDomain, clock);
	}

}
