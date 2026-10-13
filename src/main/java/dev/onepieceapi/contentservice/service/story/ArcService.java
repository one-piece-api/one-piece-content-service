package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.persistence.entity.ArcVersionEntity;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.content.ContentService;
import dev.onepieceapi.contentservice.service.validation.ArcValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Arcs and their versions: the whole workflow of {@link ContentService}, run with the
 * {@link ArcDefinition}.
 */
@Service
public class ArcService extends ContentService<Arc, ArcVersionEntity> {

	@Autowired
	public ArcService(ArcVersionRepository versionRepository, ContentVersionRepository contentVersionRepository,
			ContentRepository contentRepository, ArcValidator validator, ArcRules rules,
			AuditLogService auditLogService, Clock clock) {
		super(new ArcDefinition(versionRepository, validator, rules), contentVersionRepository, contentRepository,
				auditLogService, clock);
	}

}
