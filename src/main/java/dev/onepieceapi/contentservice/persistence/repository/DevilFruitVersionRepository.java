package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.projection.TypeFruitCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The versions of Devil Fruits, content and workflow together. Every query common to all
 * entities comes from {@link VersionBodyRepository}; here, only the two that read the
 * entity's own table by name.
 */
public interface DevilFruitVersionRepository extends VersionBodyRepository<DevilFruitVersionEntity> {

	/**
	 * How many fruits each of these types has for a caller seeing these statuses: the
	 * fruits whose most recent visible version points to it - the version their list
	 * shows (see {@code VersionBodySpecifications#mostRecentIn}).
	 */
	@Query("""
			select new dev.onepieceapi.contentservice.persistence.projection.TypeFruitCount(
				d.typeContentId, count(d))
			from DevilFruitVersionEntity d join d.version v
			where d.typeContentId in :typeContentIds and v.status in :statuses
				and v.versionNumber = (select max(o.versionNumber) from ContentVersionEntity o
					where o.contentId = v.contentId and o.status in :statuses)
			group by d.typeContentId""")
	List<TypeFruitCount> countByTypes(Collection<UUID> typeContentIds, Collection<VersionStatus> statuses);

	/** Whether a version in one of these statuses has this image. */
	@Query("""
			select count(d) > 0 from DevilFruitVersionEntity d join d.version v
			where d.imageId = :imageId and v.status in :statuses""")
	boolean usesImage(String imageId, Collection<VersionStatus> statuses);

	/** How many fruits are online with this type. */
	@Query("""
			select count(d) from DevilFruitVersionEntity d join d.version v
			where d.typeContentId = :typeContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineLinkedTo(UUID typeContentId);

	/** The fruits online with this type, by romaji: as many as the page asks for. */
	@Query("""
			select d from DevilFruitVersionEntity d join fetch d.version v
			where d.typeContentId = :typeContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	List<DevilFruitVersionEntity> findOnlineLinkedTo(UUID typeContentId, Pageable pageable);

	/**
	 * The fruits online with this type naming a subcategory outside the given ones, by
	 * romaji: as many as the page asks for, and how many there are.
	 */
	default Page<DevilFruitVersionEntity> findOnlineOutsideSubcategories(UUID typeContentId, Set<UUID> subcategoryIds,
			Pageable pageable) {
		return subcategoryIds.isEmpty() ? findOnlineWithSubcategory(typeContentId, pageable)
				: findOnlineWithSubcategoryNotIn(typeContentId, subcategoryIds, pageable);
	}

	@Query("""
			select d from DevilFruitVersionEntity d join d.version v
			where d.typeContentId = :typeContentId and d.subcategoryId is not null
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	Page<DevilFruitVersionEntity> findOnlineWithSubcategory(UUID typeContentId, Pageable pageable);

	@Query("""
			select d from DevilFruitVersionEntity d join d.version v
			where d.typeContentId = :typeContentId and d.subcategoryId not in :subcategoryIds
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	Page<DevilFruitVersionEntity> findOnlineWithSubcategoryNotIn(UUID typeContentId, Set<UUID> subcategoryIds,
			Pageable pageable);

	@Override
	@Query(value = """
			select exists (select 1 from devil_fruit_version d join content_version v on v.id = d.version_id
			               where d.romaji_slug = :slug and v.content_id <> :contentId)
			    or exists (select 1 from content_slug s
			               where s.entity_type = 'DEVIL_FRUIT' and s.slug = :slug and s.content_id <> :contentId)""",
			nativeQuery = true)
	boolean slugIsTakenByAnother(String slug, UUID contentId);

	@Override
	@Modifying
	@Query(value = """
			insert into content_slug (entity_type, slug, content_id, assigned_at)
			select c.entity_type, d.romaji_slug, c.id, :assignedAt
			from devil_fruit_version d
			    join content_version v on v.id = d.version_id
			    join content c on c.id = v.content_id
			where d.version_id = :versionId and d.romaji_slug is not null
			on conflict (entity_type, slug) do update set assigned_at = excluded.assigned_at
			where content_slug.content_id = excluded.content_id""", nativeQuery = true)
	int assignSlug(UUID versionId, Instant assignedAt);

}
