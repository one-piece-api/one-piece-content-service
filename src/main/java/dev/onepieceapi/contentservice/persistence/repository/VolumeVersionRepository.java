package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.VolumeVersionEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * The versions of volumes, content and workflow together. Every query common to all
 * entities comes from {@link VersionBodyRepository}; here, those reading the entity's own
 * table by name, and the one telling whether a cover is still used.
 */
public interface VolumeVersionRepository extends VersionBodyRepository<VolumeVersionEntity> {

	@Override
	default String naturalKeyAttribute() {
		return VolumeVersionEntity.Fields.number;
	}

	/** Whether a version in one of these statuses has this cover. */
	@Query("""
			select count(d) > 0 from VolumeVersionEntity d join d.version v
			where d.coverImageId = :imageId and v.status in :statuses""")
	boolean usesImage(String imageId, Collection<VersionStatus> statuses);

	@Override
	@Query(value = """
			select exists (select 1 from volume_version d join content_version v on v.id = d.version_id
			               where d.number_slug = :slug and v.content_id <> :contentId)
			    or exists (select 1 from content_slug s
			               where s.entity_type = 'VOLUME' and s.slug = :slug and s.content_id <> :contentId)""",
			nativeQuery = true)
	boolean slugIsTakenByAnother(String slug, UUID contentId);

	@Override
	@Modifying
	@Query(value = """
			insert into content_slug (entity_type, slug, content_id, assigned_at)
			select c.entity_type, d.number_slug, c.id, :assignedAt
			from volume_version d
			    join content_version v on v.id = d.version_id
			    join content c on c.id = v.content_id
			where d.version_id = :versionId and d.number_slug is not null
			on conflict (entity_type, slug) do update set assigned_at = excluded.assigned_at
			where content_slug.content_id = excluded.content_id""", nativeQuery = true)
	int assignSlug(UUID versionId, Instant assignedAt);

}
