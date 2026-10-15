package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.persistence.entity.ChapterVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * The chapters as the first appearances point to them (implementation plan of the
 * timeline, T7), and those a first appearance may name.
 */
@Service
public class ChapterLinks extends NumberedLinks<Chapter, ChapterVersionEntity> {

	@Autowired
	public ChapterLinks(ChapterVersionRepository repository, Clock clock) {
		super(repository, StoryVersionMapper::toDomain, clock);
	}

}
