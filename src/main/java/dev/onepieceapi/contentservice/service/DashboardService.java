package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.config.DashboardProperties;
import dev.onepieceapi.contentservice.domain.dashboard.Activity;
import dev.onepieceapi.contentservice.domain.dashboard.ContentTitle;
import dev.onepieceapi.contentservice.domain.dashboard.MineScope;
import dev.onepieceapi.contentservice.domain.dashboard.StatusCount;
import dev.onepieceapi.contentservice.domain.dashboard.StatusFilter;
import dev.onepieceapi.contentservice.domain.dashboard.StatusRow;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.TransitionContext;
import dev.onepieceapi.contentservice.domain.workflow.TransitionPolicy;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.domain.workflow.VisibilityPolicy;
import dev.onepieceapi.contentservice.persistence.entity.AuditLogEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.AuditLogMapper;
import dev.onepieceapi.contentservice.persistence.mapper.ContentVersionMapper;
import dev.onepieceapi.contentservice.persistence.projection.StatusTally;
import dev.onepieceapi.contentservice.persistence.repository.AuditLogRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.specification.ContentVersionSpecifications;
import dev.onepieceapi.contentservice.service.exception.StatusNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
		Map<UUID, EntityType> entityTypes = entityTypesOf(contentIds);
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
	 * One page of a status, across kinds of content (UF-CNT-19): each content by its most
	 * recent version there, filtered and sorted as asked, with what the caller may do
	 * with it - the same as on its detail screen - and the two counters of the scope
	 * switch.
	 * @throws StatusNotFoundException for a status the caller does not see, or one with
	 * no page
	 */
	public StatusPage statusPage(Set<Permission> permissions, User caller, VersionStatus status, StatusFilter filter,
			Pageable pageable) {
		requirePage(status, permissions);
		Optional<MineScope> scope = MineScope.of(status, permissions);
		Page<ContentVersionEntity> page = this.versionRepository.searchStatus(status, filtersOf(filter, scope, caller),
				pageable);
		Page<StatusRow> rows = rowsOf(page, status, permissions, caller);
		long all = this.versionRepository.countStatus(status, Specification.allOf());
		return new StatusPage(rows, all, mineCount(status, permissions, caller));
	}

	/** The authors of the rows of a status page, for its author filter. */
	public List<User> statusAuthors(Set<Permission> permissions, VersionStatus status) {
		requirePage(status, permissions);
		return this.versionRepository.findAuthorsOfStatus(status);
	}

	private static void requirePage(VersionStatus status, Set<Permission> permissions) {
		if (!StatusCount.tracked().contains(status)
				|| !VisibilityPolicy.visibleStatuses(permissions).contains(status)) {
			throw new StatusNotFoundException(status);
		}
	}

	/** The filters asked for; "mine" only where the status has one. */
	private static Specification<ContentVersionEntity> filtersOf(StatusFilter filter, Optional<MineScope> scope,
			User caller) {
		List<Specification<ContentVersionEntity>> filters = new ArrayList<>();
		Optional.ofNullable(filter.entityType())
			.map(ContentVersionSpecifications::ofEntityType)
			.ifPresent(filters::add);
		Optional.ofNullable(filter.author()).map(ContentVersionSpecifications::authoredBy).ifPresent(filters::add);
		if (filter.mine()) {
			scope.map(mine -> mineOf(mine, caller)).ifPresent(filters::add);
		}
		return Specification.allOf(filters);
	}

	private static Specification<ContentVersionEntity> mineOf(MineScope scope, User caller) {
		return switch (scope) {
			case AUTHORED -> ContentVersionSpecifications.authoredByUser(caller.id());
			case CLAIMED -> ContentVersionSpecifications.claimedBy(caller.id());
		};
	}

	/**
	 * Each version of the page as a row: its kind, its title, and what the caller may do
	 * with it, decided on the whole content as everywhere else.
	 */
	private Page<StatusRow> rowsOf(Page<ContentVersionEntity> page, VersionStatus status, Set<Permission> permissions,
			User caller) {
		List<UUID> contentIds = page.map(ContentVersionEntity::getContentId).getContent();
		Map<UUID, EntityType> entityTypes = entityTypesOf(contentIds);
		Map<UUID, ContentTitle> titles = titlesOf(entityTypes, Set.of(status));
		Set<UUID> withOpenVersion = this.versionRepository.withOpenVersion(contentIds);
		Map<UUID, Integer> onlineNumbers = this.versionRepository.onlineVersionNumbers(contentIds);
		return page.map(entity -> {
			UUID contentId = entity.getContentId();
			Version<ContentTitle> version = ContentVersionMapper.toDomain(entity, titles.get(contentId));
			var context = new TransitionContext(version, withOpenVersion.contains(contentId), caller, permissions);
			return new StatusRow(entityTypes.get(contentId), contentId, version, onlineNumbers.get(contentId),
					TransitionPolicy.allowedActions(context), TransitionPolicy.overrideActions(context));
		});
	}

	/**
	 * How many of the contents in this status are the caller's; null when it has no
	 * "mine". The same condition as the page's "mine" filter.
	 */
	private Long mineCount(VersionStatus status, Set<Permission> permissions, User caller) {
		return MineScope.of(status, permissions)
			.map(scope -> this.versionRepository.countStatus(status, mineOf(scope, caller)))
			.orElse(null);
	}

	private Map<UUID, EntityType> entityTypesOf(Collection<UUID> contentIds) {
		return this.contentRepository.findByIdIn(contentIds)
			.stream()
			.collect(Collectors.toMap(ContentEntity::getId, ContentEntity::getEntityType));
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
