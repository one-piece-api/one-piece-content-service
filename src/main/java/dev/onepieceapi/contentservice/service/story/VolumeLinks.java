package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Volume;
import dev.onepieceapi.contentservice.persistence.entity.VolumeVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

/** The volumes as the chapters point to them, and those a chapter may be linked to. */
@Service
public class VolumeLinks extends NumberedLinks<Volume, VolumeVersionEntity> {

	@Autowired
	public VolumeLinks(VolumeVersionRepository repository, Clock clock) {
		super(repository, StoryVersionMapper::toDomain, clock);
	}

}
