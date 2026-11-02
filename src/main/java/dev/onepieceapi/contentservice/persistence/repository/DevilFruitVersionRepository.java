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

	@Override
	default String naturalKeyAttribute() {
		return DevilFruitVersionEntity.Fields.romaji;
	}

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

	/**
	 * How many fruits are online with this type, current, initial or revealed (workflow
	 * 3.7.1).
	 */
	@Query("""
			select count(d) from DevilFruitVersionEntity d join d.version v
			where (d.typeContentId = :typeContentId or d.initialTypeContentId = :typeContentId
					or exists (select 1 from DevilFruitRevealEntity r where r member of d.reveals
						and r.typeContentId = :typeContentId))
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineLinkedTo(UUID typeContentId);

	/**
	 * The fruits online with this type, as above, by romaji: as many as the page asks
	 * for.
	 */
	@Query("""
			select d from DevilFruitVersionEntity d join fetch d.version v
			where (d.typeContentId = :typeContentId or d.initialTypeContentId = :typeContentId
					or exists (select 1 from DevilFruitRevealEntity r where r member of d.reveals
						and r.typeContentId = :typeContentId))
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	List<DevilFruitVersionEntity> findOnlineLinkedTo(UUID typeContentId, Pageable pageable);

	/**
	 * How many fruits are online naming this chapter or episode, as first appearance, in
	 * a paragraph (ADR-0008) or as the point of a reveal (workflow 3.7.1).
	 */
	@Query("""
			select count(d) from DevilFruitVersionEntity d join d.version v
			where (d.firstAppearanceChapterId = :storyContentId or d.firstAppearanceEpisodeId = :storyContentId
					or exists (select 1 from DevilFruitParagraphEntity p where p member of d.paragraphs
						and (p.window.discoveredChapterId = :storyContentId or p.window.discoveredEpisodeId = :storyContentId
							or p.window.endedChapterId = :storyContentId or p.window.endedEpisodeId = :storyContentId))
					or exists (select 1 from DevilFruitRevealEntity r where r member of d.reveals
						and (r.revealedChapterId = :storyContentId or r.revealedEpisodeId = :storyContentId)))
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineWithPointIn(UUID storyContentId);

	/**
	 * The fruits online naming this chapter or episode, as above, by romaji: as many as
	 * the page asks for.
	 */
	@Query("""
			select d from DevilFruitVersionEntity d join fetch d.version v
			where (d.firstAppearanceChapterId = :storyContentId or d.firstAppearanceEpisodeId = :storyContentId
					or exists (select 1 from DevilFruitParagraphEntity p where p member of d.paragraphs
						and (p.window.discoveredChapterId = :storyContentId or p.window.discoveredEpisodeId = :storyContentId
							or p.window.endedChapterId = :storyContentId or p.window.endedEpisodeId = :storyContentId))
					or exists (select 1 from DevilFruitRevealEntity r where r member of d.reveals
						and (r.revealedChapterId = :storyContentId or r.revealedEpisodeId = :storyContentId)))
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	List<DevilFruitVersionEntity> findOnlineWithPointIn(UUID storyContentId, Pageable pageable);

	/**
	 * The fruits online with this type naming a subcategory outside the given ones - as
	 * current value, initial knowledge or reveal (workflow 3.7.1) - by romaji: as many as
	 * the page asks for, and how many there are.
	 */
	default Page<DevilFruitVersionEntity> findOnlineOutsideSubcategories(UUID typeContentId, Set<UUID> subcategoryIds,
			Pageable pageable) {
		return subcategoryIds.isEmpty() ? findOnlineWithSubcategory(typeContentId, pageable)
				: findOnlineWithSubcategoryNotIn(typeContentId, subcategoryIds, pageable);
	}

	@Query("""
			select d from DevilFruitVersionEntity d join d.version v
			where ((d.typeContentId = :typeContentId and d.subcategoryId is not null)
					or (d.initialTypeContentId = :typeContentId and d.initialSubcategoryId is not null)
					or exists (select 1 from DevilFruitRevealEntity r where r member of d.reveals
						and r.typeContentId = :typeContentId and r.subcategoryId is not null))
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	Page<DevilFruitVersionEntity> findOnlineWithSubcategory(UUID typeContentId, Pageable pageable);

	@Query("""
			select d from DevilFruitVersionEntity d join d.version v
			where ((d.typeContentId = :typeContentId and d.subcategoryId not in :subcategoryIds)
					or (d.initialTypeContentId = :typeContentId and d.initialSubcategoryId not in :subcategoryIds)
					or exists (select 1 from DevilFruitRevealEntity r where r member of d.reveals
						and r.typeContentId = :typeContentId and r.subcategoryId not in :subcategoryIds))
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	Page<DevilFruitVersionEntity> findOnlineWithSubcategoryNotIn(UUID typeContentId, Set<UUID> subcategoryIds,
			Pageable pageable);

	/**
	 * Every romaji of every version of another fruit counts - current, initial or
	 * revealed (workflow 3.7.1) - as do the slugs it held online.
	 */
	@Override
	@Query(value = """
			select exists (select 1 from devil_fruit_version d join content_version v on v.id = d.version_id
			               where d.romaji_slug = :slug and v.content_id <> :contentId)
			    or exists (select 1 from devil_fruit_version_initial_knowledge k
			                   join content_version v on v.id = k.version_id
			               where k.romaji_slug = :slug and v.content_id <> :contentId)
			    or exists (select 1 from devil_fruit_version_reveal r join content_version v on v.id = r.version_id
			               where r.romaji_slug = :slug and v.content_id <> :contentId)
			    or exists (select 1 from content_slug s
			               where s.entity_type = 'DEVIL_FRUIT' and s.slug = :slug and s.content_id <> :contentId)""",
			nativeQuery = true)
	boolean slugIsTakenByAnother(String slug, UUID contentId);

	/** Every romaji it holds: current, initial and revealed (workflow 3.7.1). */
	@Override
	@Modifying
	@Query(value = """
			insert into content_slug (entity_type, slug, content_id, assigned_at)
			select c.entity_type, s.slug, c.id, :assignedAt
			from (select d.romaji_slug as slug from devil_fruit_version d where d.version_id = :versionId
			      union select k.romaji_slug from devil_fruit_version_initial_knowledge k where k.version_id = :versionId
			      union select r.romaji_slug from devil_fruit_version_reveal r where r.version_id = :versionId) s
			    join content_version v on v.id = :versionId
			    join content c on c.id = v.content_id
			where s.slug is not null
			on conflict (entity_type, slug) do update set assigned_at = excluded.assigned_at
			where content_slug.content_id = excluded.content_id""",
			nativeQuery = true)
	int assignSlug(UUID versionId, Instant assignedAt);

	/**
	 * The paragraphs, initial knowledge and reveals count too: a language they are
	 * written in is in use.
	 */
	@Override
	@Query(value = """
			select exists (select 1 from devil_fruit_version_translation where language_code = :languageCode)
			    or exists (select 1 from devil_fruit_version_paragraph_translation
			               where language_code = :languageCode)
			    or exists (select 1 from devil_fruit_version_initial_knowledge_translation
			               where language_code = :languageCode)
			    or exists (select 1 from devil_fruit_version_reveal_translation
			               where language_code = :languageCode)""", nativeQuery = true)
	boolean existsByLanguage(String languageCode);

}
