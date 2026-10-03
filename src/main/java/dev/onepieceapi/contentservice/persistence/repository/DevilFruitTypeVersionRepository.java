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
	 * One page of the list: one row per content, by its most recent version among
	 * {@code statuses}, filtered and sorted as asked.
	 */
	default Page<DevilFruitTypeVersionEntity> search(Collection<VersionStatus> statuses, ContentFilter filter,
			Clock clock, Pageable pageable) {
		var specification = DevilFruitTypeVersionSpecifications.listOf(statuses, filter, clock);
		return findAll(specification, DevilFruitTypeSorting.resolve(pageable));
	}

	/**
	 * How many contents the list would show in all, with the same statuses and filters.
	 */
	default long count(Collection<VersionStatus> statuses, ContentFilter filter, Clock clock) {
		return count(DevilFruitTypeVersionSpecifications.listOf(statuses, filter, clock));
	}

	/**
	 * For each of the given contents, its most recent version among {@code statuses} -
	 * what it is called where it is listed.
	 */
	default List<DevilFruitTypeVersionEntity> findMostRecent(Collection<UUID> contentIds,
			Collection<VersionStatus> statuses) {
		return findAll(DevilFruitTypeVersionSpecifications.mostRecentIn(statuses)
			.and(DevilFruitTypeVersionSpecifications.ofContents(contentIds)));
	}

	/** The visible versions of one content, oldest first. */
	@Query("""
			select d from DevilFruitTypeVersionEntity d join fetch d.version v
			where v.contentId = :contentId and v.status in :statuses
			order by v.versionNumber""")
	List<DevilFruitTypeVersionEntity> findVisible(UUID contentId, Collection<VersionStatus> statuses);

	@Query("""
			select d from DevilFruitTypeVersionEntity d join fetch d.version v
			where v.contentId = :contentId and v.versionNumber = :versionNumber and v.status in :statuses""")
	Optional<DevilFruitTypeVersionEntity> findVisible(UUID contentId, int versionNumber,
			Collection<VersionStatus> statuses);

	/**
	 * The content's online version, if it has one - whether or not the caller sees it.
	 */
	@Query("""
			select d from DevilFruitTypeVersionEntity d join fetch d.version v
			where v.contentId = :contentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	Optional<DevilFruitTypeVersionEntity> findOnline(UUID contentId);

	/**
	 * Every version of the content but one, in any status, oldest first - whether or not
	 * the caller sees them.
	 */
	@Query("""
			select d from DevilFruitTypeVersionEntity d join fetch d.version v
			where v.contentId = :contentId and v.versionNumber <> :versionNumber
			order by v.versionNumber""")
	List<DevilFruitTypeVersionEntity> findOthers(UUID contentId, int versionNumber);

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

	/**
	 * Whether a version of another content, in any status, has this romaji - whatever the
	 * case it is written in.
	 */
	@Query("""
			select count(d) > 0 from DevilFruitTypeVersionEntity d join d.version v
			where lower(d.romaji) = lower(:romaji) and v.contentId <> :contentId""")
	boolean romajiIsTakenByAnother(String romaji, UUID contentId);

	/**
	 * Whether a version of another content, in any status, has this name in this language
	 * - whatever the case it is written in.
	 */
	@Query("""
			select count(d) > 0 from DevilFruitTypeVersionEntity d join d.version v join d.translations t
			where key(t) = :languageCode and lower(t.name) = lower(:name) and v.contentId <> :contentId""")
	boolean nameIsTakenByAnother(String languageCode, String name, UUID contentId);

}
