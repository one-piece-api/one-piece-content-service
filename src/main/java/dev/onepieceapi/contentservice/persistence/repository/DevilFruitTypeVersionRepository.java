package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

/**
 * The versions of Devil Fruit Types, content and workflow together. Every query common to
 * all entities comes from {@link VersionBodyRepository}; here, only the two that read the
 * entity's own table by name.
 */
public interface DevilFruitTypeVersionRepository extends VersionBodyRepository<DevilFruitTypeVersionEntity> {

	@Override
	@Query(value = """
			select exists (select 1 from devil_fruit_type_version d join content_version v on v.id = d.version_id
			               where d.romaji_slug = :slug and v.content_id <> :contentId)
			    or exists (select 1 from content_slug s
			               where s.entity_type = 'DEVIL_FRUIT_TYPE' and s.slug = :slug and s.content_id <> :contentId)""",
			nativeQuery = true)
	boolean slugIsTakenByAnother(String slug, UUID contentId);

	@Override
	@Modifying
	@Query(value = """
			insert into content_slug (entity_type, slug, content_id, assigned_at)
			select c.entity_type, d.romaji_slug, c.id, :assignedAt
			from devil_fruit_type_version d
			    join content_version v on v.id = d.version_id
			    join content c on c.id = v.content_id
			where d.version_id = :versionId and d.romaji_slug is not null
			on conflict (entity_type, slug) do update set assigned_at = excluded.assigned_at
			where content_slug.content_id = excluded.content_id""", nativeQuery = true)
	int assignSlug(UUID versionId, Instant assignedAt);

}
