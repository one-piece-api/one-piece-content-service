package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.persistence.entity.AuditLogEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

	/** The history of one version, oldest first. */
	List<AuditLogEntity> findByTargetVersionIdOrderByOccurredAtAscIdAsc(UUID targetVersionId);

}
