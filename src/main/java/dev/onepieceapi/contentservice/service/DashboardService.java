package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.config.DashboardProperties;
import dev.onepieceapi.contentservice.domain.dashboard.Activity;
import dev.onepieceapi.contentservice.domain.dashboard.ContentTitle;
import dev.onepieceapi.contentservice.domain.dashboard.MineScope;
import dev.onepieceapi.contentservice.domain.dashboard.StatusCount;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.domain.workflow.VisibilityPolicy;
import dev.onepieceapi.contentservice.persistence.entity.AuditLogEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.AuditLogMapper;
import dev.onepieceapi.contentservice.persistence.projection.StatusTally;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The dashboard (UF-CNT-19): a counter per status the caller sees, across entity types,
 * and the caller's own latest actions. Everything is limited to what the caller may see
 * (VisibilityPolicy), as on every other screen.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DashboardService {

	private final ContentVersionRepository versionRepository;

	private final ContentRepository contentRepository;

	private final AuditLogRepository auditLogRepository;

	private final List<ContentTitleSource> titleSources;

	private final DashboardProperties properties;

	/**
	 * One counter per status that has a tile and is visible to the caller, in workflow
	 * order - zero included - with the caller's share where the status has one.
	 */
	public List<StatusCount> statusCounts(Set<Permission> permissions, User caller) {
		Set<VersionStatus> statuses = EnumSet.copyOf(StatusCount.tracked());
		statuses.retainAll(VisibilityPolicy.visibleStatuses(permissions));
		if (statuses.isEmpty()) {
			return List.of();
		}
		Map<VersionStatus, Long> tallies = this.versionRepository.countContentsByStatus(statuses)
			.stream()
			.collect(Collectors.toMap(StatusTally::status, StatusTally::contents));
		return statuses.stream()
			.map(status -> new StatusCount(status, tallies.getOrDefault(status, 0L),
					mineCount(status, permissions, caller)))
			.toList();
	}

	/**
	 * The caller's latest actions on contents, most recent first: never anyone else's.
	 * Each one is named as its content is called now when the caller still sees it,
	 * otherwise by what it was called then.
	 */
	public List<Activity> activity(Set<Permission> permissions, User caller) {
		List<AuditLogEntity> records = this.auditLogRepository
			.findByActorUserIdAndTargetContentIdNotNullOrderByOccurredAtDescIdDesc(caller.id(),
					Limit.of(this.properties.activitySize()));
		Set<UUID> contentIds = records.stream().map(AuditLogEntity::getTargetContentId).collect(Collectors.toSet());
		Set<UUID> versionIds = records.stream()
			.map(AuditLogEntity::getTargetVersionId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		Map<UUID, EntityType> entityTypes = this.contentRepository.findByIdIn(contentIds)
			.stream()
			.collect(Collectors.toMap(ContentEntity::getId, ContentEntity::getEntityType));
		Map<UUID, Integer> versionNumbers = this.versionRepository.findByIdIn(versionIds)
			.stream()
			.collect(Collectors.toMap(ContentVersionEntity::getId, ContentVersionEntity::getVersionNumber));
		Map<UUID, ContentTitle> titles = titlesOf(entityTypes, VisibilityPolicy.visibleStatuses(permissions));
		return records.stream()
			.map(entity -> AuditLogMapper.toActivity(entity, entityTypes.get(entity.getTargetContentId()),
					versionNumbers.get(entity.getTargetVersionId()), titles.get(entity.getTargetContentId())))
			.toList();
	}

	/**
	 * How many of the contents in this status are the caller's; null when it has no
	 * "mine".
	 */
	private Long mineCount(VersionStatus status, Set<Permission> permissions, User caller) {
		return MineScope.of(status, permissions).map(scope -> switch (scope) {
			case AUTHORED -> this.versionRepository.countByStatusAndAuthorUserId(status, caller.id());
			case CLAIMED -> this.versionRepository.countByStatusAndClaimantUserId(status, caller.id());
		}).orElse(null);
	}

	/** Asks each entity type for the titles of its own contents. */
	private Map<UUID, ContentTitle> titlesOf(Map<UUID, EntityType> entityTypes, Set<VersionStatus> visible) {
		Map<UUID, ContentTitle> titles = new HashMap<>();
		for (ContentTitleSource source : this.titleSources) {
			Set<UUID> ofType = entityTypes.entrySet()
				.stream()
				.filter(entry -> entry.getValue() == source.entityType())
				.map(Map.Entry::getKey)
				.collect(Collectors.toSet());
			titles.putAll(source.titles(ofType, visible));
		}
		return titles;
	}

}
