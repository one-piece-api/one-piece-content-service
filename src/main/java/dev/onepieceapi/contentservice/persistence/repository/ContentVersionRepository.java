package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.projection.OnlineVersion;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What can be asked of the versions of any entity type, from their workflow alone.
 * Reading a version with its content goes through its entity's own repository, e.g.
 * {@link DevilFruitTypeVersionRepository}.
 */
public interface ContentVersionRepository extends Repository<ContentVersionEntity, UUID> {

	@Query("""
			select new dev.onepieceapi.contentservice.persistence.projection.OnlineVersion(v.itemId, v.versionNumber)
			from ContentVersionEntity v
			where v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
				and v.itemId in :itemIds""")
	List<OnlineVersion> findOnline(Collection<UUID> itemIds);

	/**
	 * For each of the given items that has something online, the number of that version.
	 */
	default Map<UUID, Integer> onlineVersionNumbers(Collection<UUID> itemIds) {
		return findOnline(itemIds).stream()
			.collect(Collectors.toMap(OnlineVersion::itemId, OnlineVersion::versionNumber));
	}

}
