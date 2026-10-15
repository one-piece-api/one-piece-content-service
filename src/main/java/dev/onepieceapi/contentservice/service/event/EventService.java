package dev.onepieceapi.contentservice.service.event;

import dev.onepieceapi.contentservice.domain.event.Event;
import dev.onepieceapi.contentservice.persistence.entity.EventVersionEntity;
import dev.onepieceapi.contentservice.persistence.repository.EventVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.content.ContentService;
import dev.onepieceapi.contentservice.service.validation.EventValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Events and their versions: the whole workflow of {@link ContentService}, run with the
 * {@link EventDefinition}.
 */
@Service
public class EventService extends ContentService<Event, EventVersionEntity> {

	@Autowired
	public EventService(EventVersionRepository versionRepository, ContentVersionRepository contentVersionRepository,
			ContentRepository contentRepository, EventValidator validator, EventRules rules,
			AuditLogService auditLogService, Clock clock) {
		super(new EventDefinition(versionRepository, validator, rules), contentVersionRepository, contentRepository,
				auditLogService, clock);
	}

}
