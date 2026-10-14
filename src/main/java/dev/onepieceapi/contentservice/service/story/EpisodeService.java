package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.persistence.entity.EpisodeVersionEntity;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.content.ContentService;
import dev.onepieceapi.contentservice.service.validation.EpisodeValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Episodes and their versions: the whole workflow of {@link ContentService}, run with the
 * {@link EpisodeDefinition}.
 */
@Service
public class EpisodeService extends ContentService<Episode, EpisodeVersionEntity> {

	@Autowired
	public EpisodeService(EpisodeVersionRepository versionRepository, ContentVersionRepository contentVersionRepository,
			ContentRepository contentRepository, EpisodeValidator validator, EpisodeRules rules,
			AuditLogService auditLogService, Clock clock) {
		super(new EpisodeDefinition(versionRepository, validator, rules), contentVersionRepository, contentRepository,
				auditLogService, clock);
	}

}
