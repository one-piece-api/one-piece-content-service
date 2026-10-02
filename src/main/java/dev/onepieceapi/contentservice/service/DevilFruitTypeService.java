package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentListSummary;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionEvent;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.domain.workflow.VisibilityPolicy;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.service.exception.DevilFruitTypeNotFoundException;
import dev.onepieceapi.contentservice.service.exception.VersionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reads Devil Fruit Types and their versions
 * (docs/user-flows/content-editorial-workflow.md UF-CNT-12, UF-CNT-18). Every method
 * starts from the caller's permissions: {@link VisibilityPolicy} turns them into the
 * statuses they may see, and nothing outside those statuses is ever loaded. Building the
 * queries, converting rows and validating requests are done elsewhere: this class only
 * decides what to read and for whom.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitTypeService {

	private final DevilFruitTypeVersionRepository versionRepository;

	private final ContentVersionRepository contentVersionRepository;

	private final AuditLogService auditLogService;

	private final Clock clock;

	/**
	 * One row per content: its most recent visible version or, when filtering by status,
	 * its visible version in that status. The other filters apply to that row.
	 */
	public Page<ContentSummary<DevilFruitType>> list(Set<Permission> permissions, ContentFilter filter,
			Pageable pageable) {
		Set<VersionStatus> statuses = filter.statusesAmong(VisibilityPolicy.visibleStatuses(permissions));
		if (statuses.isEmpty()) {
			return Page.empty(pageable);
		}
		Page<DevilFruitTypeVersionEntity> page = this.versionRepository.search(statuses, filter, this.clock, pageable);
		Map<UUID, Integer> onlineNumbers = this.contentVersionRepository.onlineVersionNumbers(contentIdsOf(page));
		return page.map(version -> {
			Integer onlineNumber = onlineNumbers.get(version.getContentId());
			return DevilFruitTypeVersionMapper.toSummary(version, onlineNumber);
		});
	}

	/**
	 * The distinct authors of the versions the caller sees - the list's author filter.
	 */
	public List<User> authors(Set<Permission> permissions) {
		return this.versionRepository.findAuthors(VisibilityPolicy.visibleStatuses(permissions));
	}

	/**
	 * What surrounds the list, whatever filter is on: how many contents the caller sees,
	 * how many of those are shown by a version of their own, and the statuses they may
	 * filter by.
	 */
	public ContentListSummary summary(Set<Permission> permissions, User caller) {
		Set<VersionStatus> statuses = VisibilityPolicy.visibleStatuses(permissions);
		if (statuses.isEmpty()) {
			return new ContentListSummary(0, 0, statuses);
		}
		long total = this.versionRepository.count(statuses, ContentFilter.none(), this.clock);
		long mine = this.versionRepository.count(statuses, ContentFilter.authoredBy(caller.username()), this.clock);
		return new ContentListSummary(total, mine, statuses);
	}

	public Content<DevilFruitType> get(Set<Permission> permissions, UUID contentId) {
		Set<VersionStatus> statuses = VisibilityPolicy.visibleStatuses(permissions);
		List<DevilFruitTypeVersionEntity> versions = this.versionRepository.findVisible(contentId, statuses);
		if (versions.isEmpty()) {
			throw new DevilFruitTypeNotFoundException(contentId);
		}
		return DevilFruitTypeVersionMapper.toContent(contentId, versions);
	}

	public Version<DevilFruitType> getVersion(Set<Permission> permissions, UUID contentId, int versionNumber) {
		return DevilFruitTypeVersionMapper.toDomain(visibleVersion(permissions, contentId, versionNumber));
	}

	/** The history of a version the caller sees, oldest first. */
	public List<VersionEvent> events(Set<Permission> permissions, UUID contentId, int versionNumber) {
		DevilFruitTypeVersionEntity version = visibleVersion(permissions, contentId, versionNumber);
		return this.auditLogService.versionEvents(version.getVersionId());
	}

	private DevilFruitTypeVersionEntity visibleVersion(Set<Permission> permissions, UUID contentId, int versionNumber) {
		Set<VersionStatus> statuses = VisibilityPolicy.visibleStatuses(permissions);
		return this.versionRepository.findVisible(contentId, versionNumber, statuses)
			.orElseThrow(() -> new VersionNotFoundException(contentId, versionNumber));
	}

	private static List<UUID> contentIdsOf(Page<DevilFruitTypeVersionEntity> page) {
		return page.getContent().stream().map(DevilFruitTypeVersionEntity::getContentId).toList();
	}

}
