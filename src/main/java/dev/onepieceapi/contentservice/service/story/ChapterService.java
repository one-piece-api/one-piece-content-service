package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.persistence.entity.ChapterVersionEntity;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.content.ContentService;
import dev.onepieceapi.contentservice.service.validation.ChapterValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Chapters and their versions: the whole workflow of {@link ContentService}, run with the
 * {@link ChapterDefinition}.
 */
@Service
public class ChapterService extends ContentService<Chapter, ChapterVersionEntity> {

	@Autowired
	public ChapterService(ChapterVersionRepository versionRepository, ContentVersionRepository contentVersionRepository,
			ContentRepository contentRepository, ChapterValidator validator, ChapterRules rules,
			AuditLogService auditLogService, Clock clock) {
		super(new ChapterDefinition(versionRepository, validator, rules), contentVersionRepository, contentRepository,
				auditLogService, clock);
	}

}
