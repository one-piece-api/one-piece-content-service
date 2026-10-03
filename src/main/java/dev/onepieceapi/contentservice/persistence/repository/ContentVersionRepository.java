package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.projection.OnlineVersion;
import dev.onepieceapi.contentservice.persistence.projection.StatusTally;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What can be asked of the versions of any entity type, from their workflow alone.
 * Reading a version with its content goes through its entity's own repository, e.g.
 * {@link DevilFruitTypeVersionRepository}.
 */
public interface ContentVersionRepository extends Repository<ContentVersionEntity, UUID> {

	@Query("""
			select new dev.onepieceapi.contentservice.persistence.projection.OnlineVersion(v.contentId, v.versionNumber)
			from ContentVersionEntity v
			where v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
				and v.contentId in :contentIds""")
	List<OnlineVersion> findOnline(Collection<UUID> contentIds);

	/**
	 * For each of the given contents that has something online, the number of that
	 * version.
	 */
	default Map<UUID, Integer> onlineVersionNumbers(Collection<UUID> contentIds) {
		return findOnline(contentIds).stream()
			.collect(Collectors.toMap(OnlineVersion::contentId, OnlineVersion::versionNumber));
	}

	/** The highest version number of the content, in any status. */
	@Query("select max(v.versionNumber) from ContentVersionEntity v where v.contentId = :contentId")
	int findLatestNumber(UUID contentId);

	boolean existsByContentIdAndStatusIn(UUID contentId, Collection<VersionStatus> statuses);

	/**
	 * Whether the content has a version still moving through the workflow - whoever may
	 * see it.
	 */
	default boolean hasOpenVersion(UUID contentId) {
		return existsByContentIdAndStatusIn(contentId, VersionStatus.open());
	}

	@Query("""
			select distinct v.contentId from ContentVersionEntity v
			where v.contentId in :contentIds and v.status in :statuses""")
	Set<UUID> findContentIdsByStatusIn(Collection<UUID> contentIds, Collection<VersionStatus> statuses);

	/** Which of the given contents have a version still moving through the workflow. */
	default Set<UUID> withOpenVersion(Collection<UUID> contentIds) {
		return findContentIdsByStatusIn(contentIds, VersionStatus.open());
	}

	/**
	 * For each of {@code statuses} that has any, how many contents have a version in it -
	 * contents, not versions: a content may have several archived or retired ones.
	 */
	@Query("""
			select new dev.onepieceapi.contentservice.persistence.projection.StatusTally(v.status, count(distinct v.contentId))
			from ContentVersionEntity v
			where v.status in :statuses
			group by v.status""")
	List<StatusTally> countContentsByStatus(Collection<VersionStatus> statuses);

	/**
	 * How many versions in this status the user wrote - one per content, for an open
	 * status.
	 */
	long countByStatusAndAuthorUserId(VersionStatus status, UUID userId);

	/**
	 * How many versions in this status the user holds - only a version in review has a
	 * claimant.
	 */
	long countByStatusAndClaimantUserId(VersionStatus status, UUID userId);

	List<ContentVersionEntity> findByIdIn(Collection<UUID> ids);

}
