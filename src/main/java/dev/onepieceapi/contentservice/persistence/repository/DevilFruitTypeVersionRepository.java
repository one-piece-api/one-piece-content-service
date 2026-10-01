package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.specification.DevilFruitTypeSorting;
import dev.onepieceapi.contentservice.persistence.specification.DevilFruitTypeVersionSpecifications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The versions of Devil Fruit Types, content and workflow together. Only versions of this
 * entity have a row here, so no query needs to filter by entity type. Every read takes
 * the statuses the caller may see: visibility is part of the query, not a filter applied
 * afterwards.
 */
public interface DevilFruitTypeVersionRepository extends JpaRepository<DevilFruitTypeVersionEntity, UUID>,
		JpaSpecificationExecutor<DevilFruitTypeVersionEntity> {

	/**
	 * One page of the list: one row per item, by its most recent version among
	 * {@code statuses}, filtered and sorted as asked.
	 */
	default Page<DevilFruitTypeVersionEntity> search(Collection<VersionStatus> statuses, ContentFilter filter,
			Clock clock, Pageable pageable) {
		var specification = DevilFruitTypeVersionSpecifications.listOf(statuses, filter, clock);
		return findAll(specification, DevilFruitTypeSorting.resolve(pageable));
	}

	/** The visible versions of one item, oldest first. */
	@Query("""
			select d from DevilFruitTypeVersionEntity d join fetch d.version v
			where v.itemId = :itemId and v.status in :statuses
			order by v.versionNumber""")
	List<DevilFruitTypeVersionEntity> findVisible(UUID itemId, Collection<VersionStatus> statuses);

	@Query("""
			select d from DevilFruitTypeVersionEntity d join fetch d.version v
			where v.itemId = :itemId and v.versionNumber = :versionNumber and v.status in :statuses""")
	Optional<DevilFruitTypeVersionEntity> findVisible(UUID itemId, int versionNumber,
			Collection<VersionStatus> statuses);

	@Query("""
			select distinct new dev.onepieceapi.contentservice.domain.security.User(
				v.author.userId, v.author.username, v.author.email)
			from DevilFruitTypeVersionEntity d join d.version v
			where v.status in :statuses
			order by v.author.username""")
	List<User> findAuthors(Collection<VersionStatus> statuses);

	@Query("""
			select count(d) > 0 from DevilFruitTypeVersionEntity d join d.translations t
			where key(t) = :languageCode""")
	boolean existsByLanguage(String languageCode);

}
