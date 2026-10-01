package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.workflow.VersionEvent;
import dev.onepieceapi.contentservice.persistence.entity.AuditLogEntity;
import lombok.experimental.UtilityClass;

/** From audit records to what the domain reads out of them. */
@UtilityClass
public class AuditLogMapper {

	/** An audit record about a version, as one step of that version's history. */
	public VersionEvent toVersionEvent(AuditLogEntity entity) {
		var actor = UserMapper.toDomain(entity.getActor());
		return new VersionEvent(entity.getAction(), actor, entity.getDetail(), entity.getOccurredAt());
	}

}
