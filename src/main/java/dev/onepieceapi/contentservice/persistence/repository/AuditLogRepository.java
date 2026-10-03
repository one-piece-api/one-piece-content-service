package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.persistence.entity.AuditLogEntity;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

	/** The history of one version, oldest first. */
	List<AuditLogEntity> findByTargetVersionIdOrderByOccurredAtAscIdAsc(UUID targetVersionId);

	/**
	 * The latest records of one actor about a content, most recent first - the records
	 * about the language catalog have no content and are left out.
	 */
	List<AuditLogEntity> findByActorUserIdAndTargetContentIdNotNullOrderByOccurredAtDescIdDesc(UUID actorUserId,
			Limit limit);

}
