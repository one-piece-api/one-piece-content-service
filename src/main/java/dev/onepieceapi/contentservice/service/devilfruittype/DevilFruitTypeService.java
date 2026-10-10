package dev.onepieceapi.contentservice.service.devilfruittype;

import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.content.ContentService;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.service.validation.DevilFruitTypeValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Devil Fruit Types and their versions: the whole workflow of {@link ContentService}, run
 * with the {@link DevilFruitTypeDefinition}.
 */
@Service
public class DevilFruitTypeService extends ContentService<DevilFruitType, DevilFruitTypeVersionEntity> {

	@Autowired
	public DevilFruitTypeService(DevilFruitTypeVersionRepository versionRepository,
			ContentVersionRepository contentVersionRepository, ContentRepository contentRepository,
			DevilFruitTypeValidator validator, DevilFruitTypeRules rules, AuditLogService auditLogService,
			Clock clock) {
		super(new DevilFruitTypeDefinition(versionRepository, validator, rules), contentVersionRepository,
				contentRepository, auditLogService, clock);
	}

}
