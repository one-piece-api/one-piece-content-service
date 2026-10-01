package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.persistence.entity.AuditLogEntity;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Writes one audit record per mutating action
 * (docs/user-flows/content-editorial-workflow.md 7). A direct service over
 * {@code AuditLogRepository}, not a port/adapter pair: this service has exactly one real
 * implementation of "persist an audit record" (its own Postgres) and no external system
 * to abstract away, unlike {@code one-piece-user-service}'s Keycloak-facing ports.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class AuditLogService {

	private final AuditLogRepository repository;

	private final Clock clock;

	public void record(String action, UUID actorUserId, String actorEmail, UUID targetItemId, String targetLabel,
			String detail) {
		var entity = new AuditLogEntity(action, actorUserId, actorEmail, targetItemId, targetLabel, detail,
				this.clock.instant());
		this.repository.save(entity);
	}

}
