package dev.onepieceapi.contentservice.service.event;

import dev.onepieceapi.contentservice.domain.event.Event;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.EventVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.EventVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.EventVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentDefinition;
import dev.onepieceapi.contentservice.service.exception.EventNotFoundException;
import dev.onepieceapi.contentservice.service.validation.EventValidator;
import dev.onepieceapi.exception.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.UUID;

/** The event, as the generic workflow sees it. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
class EventDefinition implements ContentDefinition<Event, EventVersionEntity> {

	private final EventVersionRepository repository;

	private final EventValidator validator;

	private final EventRules rules;

	@Override
	public EntityType entityType() {
		return EntityType.EVENT;
	}

	@Override
	public Version<Event> toDomain(EventVersionEntity entity) {
		return EventVersionMapper.toDomain(entity);
	}

	@Override
	public EventVersionEntity newVersion(ContentVersionEntity workflow) {
		return new EventVersionEntity(workflow);
	}

	@Override
	public void rewrite(EventVersionEntity entity, Event body, Instant now) {
		EventVersionMapper.rewrite(entity, body, now);
	}

	@Override
	public NotFoundException notFound(UUID contentId) {
		return new EventNotFoundException(contentId);
	}

}
