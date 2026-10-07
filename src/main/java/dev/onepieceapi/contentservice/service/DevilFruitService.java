package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.service.validation.DevilFruitValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Devil Fruits and their versions: the whole workflow of {@link ContentService}, run with
 * the {@link DevilFruitDefinition}.
 */
@Service
public class DevilFruitService extends ContentService<DevilFruit, DevilFruitVersionEntity> {

	@Autowired
	public DevilFruitService(DevilFruitVersionRepository versionRepository,
			ContentVersionRepository contentVersionRepository, ContentRepository contentRepository,
			DevilFruitValidator validator, DevilFruitRules rules, AuditLogService auditLogService, Clock clock) {
		super(new DevilFruitDefinition(versionRepository, validator, rules), contentVersionRepository,
				contentRepository, auditLogService, clock);
	}

}
