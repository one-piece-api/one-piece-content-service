package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.persistence.AuditLogEntity;
import dev.onepieceapi.contentservice.persistence.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Writes one audit record per mutating action
 * (docs/user-flows/authentication-and-user-management.md 6). A direct service over
 * {@code AuditLogRepository}, not a port/adapter pair: this service has exactly one real
 * implementation of "persist an audit record" (its own Postgres) and no external system
 * to abstract away, unlike {@code one-piece-user-service}'s Keycloak-facing ports - see
 * {@code docs/implementation-plan-content.md} 2's architecture decision. Not yet called
 * by any endpoint (Phase 0 has none of its own besides the read-only placeholder health
 * check); ready for Step 1's first mutating endpoint to use.
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
