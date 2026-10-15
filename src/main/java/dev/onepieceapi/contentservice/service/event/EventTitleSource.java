package dev.onepieceapi.contentservice.service.event;

import dev.onepieceapi.contentservice.domain.event.Event;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.persistence.entity.EventVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.EventVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.EventVersionRepository;
import dev.onepieceapi.contentservice.service.content.VersionBodyTitleSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * An event is called by its title in each language; it has no natural key to fall back
 * on.
 */
@Component
class EventTitleSource extends VersionBodyTitleSource<Event, EventVersionEntity> {

	@Autowired
	EventTitleSource(EventVersionRepository repository) {
		super(EntityType.EVENT, repository, EventVersionMapper::toDomain);
	}

}
