package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.persistence.entity.SagaVersionEntity;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.content.ContentService;
import dev.onepieceapi.contentservice.service.validation.SagaValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Sagas and their versions: the whole workflow of {@link ContentService}, run with the
 * {@link SagaDefinition}.
 */
@Service
public class SagaService extends ContentService<Saga, SagaVersionEntity> {

	@Autowired
	public SagaService(SagaVersionRepository versionRepository, ContentVersionRepository contentVersionRepository,
			ContentRepository contentRepository, SagaValidator validator, SagaRules rules,
			AuditLogService auditLogService, Clock clock) {
		super(new SagaDefinition(versionRepository, validator, rules), contentVersionRepository, contentRepository,
				auditLogService, clock);
	}

}
