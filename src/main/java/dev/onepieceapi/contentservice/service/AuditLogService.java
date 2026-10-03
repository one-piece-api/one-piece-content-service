package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.VersionEvent;
import dev.onepieceapi.contentservice.persistence.entity.AuditLogEntity;
import dev.onepieceapi.contentservice.persistence.mapper.AuditLogMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Writes one audit record per mutating action
 * (docs/user-flows/content-editorial-workflow.md 7), and reads them back as the history
 * of a version - the audit log is its single source (implementation plan, D4). A direct
 * service over {@code AuditLogRepository}, not a port/adapter pair: this service has
 * exactly one real implementation of "persist an audit record" (its own Postgres) and no
 * external system to abstract away, unlike {@code one-piece-user-service}'s
 * Keycloak-facing ports.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class AuditLogService {

	private final AuditLogRepository repository;

	private final Clock clock;

	public void record(String action, User actor, UUID targetContentId, String targetLabel, String detail) {
		this.repository.save(
				entry(action, actor).targetContentId(targetContentId).targetLabel(targetLabel).detail(detail).build());
	}

	/**
	 * A record about one version of a content - what the history of that version is read
	 * from.
	 * @param label what the version was called when the action happened
	 */
	public void recordOnVersion(String action, User actor, UUID contentId, UUID versionId, String label) {
		recordOnVersion(action, actor, contentId, versionId, label, null);
	}

	/**
	 * A record about one version that carries something with it, e.g. the reason of a
	 * rejection.
	 */
	public void recordOnVersion(String action, User actor, UUID contentId, UUID versionId, String label,
			String detail) {
		this.repository.save(entry(action, actor).targetContentId(contentId)
			.targetVersionId(versionId)
			.targetLabel(label)
			.detail(detail)
			.build());
	}

	private AuditLogEntity.AuditLogEntityBuilder entry(String action, User actor) {
		return AuditLogEntity.builder()
			.action(action)
			.actor(UserMapper.toEmbeddable(actor))
			.occurredAt(this.clock.instant());
	}

	/**
	 * The records about one version, oldest first. Whether the caller may see that
	 * version is the caller's concern, see {@code DevilFruitTypeService}.
	 */
	public List<VersionEvent> versionEvents(UUID versionId) {
		return this.repository.findByTargetVersionIdOrderByOccurredAtAscIdAsc(versionId)
			.stream()
			.map(AuditLogMapper::toVersionEvent)
			.toList();
	}

}
