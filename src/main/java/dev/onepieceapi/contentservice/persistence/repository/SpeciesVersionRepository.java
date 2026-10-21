package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.SpeciesVersionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The versions of Species, content and workflow together. Every query common to all
 * entities comes from {@link VersionBodyRepository}; here, only those that read the
 * entity's own tables by name, the image's and the first appearance's.
 */
public interface SpeciesVersionRepository extends VersionBodyRepository<SpeciesVersionEntity> {

	@Override
	default String naturalKeyAttribute() {
		return SpeciesVersionEntity.Fields.romaji;
	}

	@Override
	@Query(value = """
			select exists (select 1 from species_version d join content_version v on v.id = d.version_id
			               where d.romaji_slug = :slug and v.content_id <> :contentId)
			    or exists (select 1 from content_slug s
			               where s.entity_type = 'SPECIES' and s.slug = :slug and s.content_id <> :contentId)""",
			nativeQuery = true)
	boolean slugIsTakenByAnother(String slug, UUID contentId);

	@Override
	@Modifying
	@Query(value = """
			insert into content_slug (entity_type, slug, content_id, assigned_at)
			select c.entity_type, d.romaji_slug, c.id, :assignedAt
			from species_version d
			    join content_version v on v.id = d.version_id
			    join content c on c.id = v.content_id
			where d.version_id = :versionId and d.romaji_slug is not null
			on conflict (entity_type, slug) do update set assigned_at = excluded.assigned_at
			where content_slug.content_id = excluded.content_id""", nativeQuery = true)
	int assignSlug(UUID versionId, Instant assignedAt);

	/** Whether a version in one of these statuses has this image. */
	@Query("""
			select count(d) > 0 from SpeciesVersionEntity d join d.version v
			where d.imageId = :imageId and v.status in :statuses""")
	boolean usesImage(String imageId, Collection<VersionStatus> statuses);

	/**
	 * How many species are online first appearing in this chapter or episode (ADR-0008).
	 */
	@Query("""
			select count(d) from SpeciesVersionEntity d join d.version v
			where (d.firstAppearanceChapterId = :storyContentId or d.firstAppearanceEpisodeId = :storyContentId)
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineFirstAppearingIn(UUID storyContentId);

	/**
	 * The species online first appearing in this chapter or episode, by romaji: as many
	 * as the page asks for.
	 */
	@Query("""
			select d from SpeciesVersionEntity d join fetch d.version v
			where (d.firstAppearanceChapterId = :storyContentId or d.firstAppearanceEpisodeId = :storyContentId)
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.romaji""")
	List<SpeciesVersionEntity> findOnlineFirstAppearingIn(UUID storyContentId, Pageable pageable);

}
