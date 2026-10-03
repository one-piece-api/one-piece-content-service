package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.dashboard.Activity;
import dev.onepieceapi.contentservice.domain.dashboard.ContentTitle;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionEvent;
import dev.onepieceapi.contentservice.persistence.entity.AuditLogEntity;
import lombok.experimental.UtilityClass;

/** From audit records to what the domain reads out of them. */
@UtilityClass
public class AuditLogMapper {

	/**
	 * An audit record about a content, as one of its actor's latest actions - with what
	 * the record does not hold itself: the content's type and what it is called now, and
	 * the number of the version acted on.
	 */
	public Activity toActivity(AuditLogEntity entity, EntityType entityType, Integer versionNumber,
			ContentTitle title) {
		return Activity.builder()
			.action(entity.getAction())
			.occurredAt(entity.getOccurredAt())
			.entityType(entityType)
			.contentId(entity.getTargetContentId())
			.versionNumber(versionNumber)
			.label(entity.getTargetLabel())
			.title(title)
			.build();
	}

	/** An audit record about a version, as one step of that version's history. */
	public VersionEvent toVersionEvent(AuditLogEntity entity) {
		return VersionEvent.builder()
			.action(entity.getAction())
			.actor(UserMapper.toDomain(entity.getActor()))
			.detail(entity.getDetail())
			.override(entity.isOverride())
			.occurredAt(entity.getOccurredAt())
			.build();
	}

}
