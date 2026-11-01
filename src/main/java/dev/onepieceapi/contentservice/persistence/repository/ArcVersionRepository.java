package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.persistence.entity.ArcVersionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The versions of arcs, content and workflow together. Every query common to all entities
 * comes from {@link VersionBodyRepository}; here, the arcs online in a saga, and those
 * reading the entity's own table by name.
 */
public interface ArcVersionRepository extends VersionBodyRepository<ArcVersionEntity> {

	@Override
	default String naturalKeyAttribute() {
		return ArcVersionEntity.Fields.number;
	}

	/** How many arcs are online in this saga. */
	@Query("""
			select count(d) from ArcVersionEntity d join d.version v
			where d.sagaContentId = :sagaContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineLinkedTo(UUID sagaContentId);

	/** The arcs online in this saga, by number: as many as the page asks for. */
	@Query("""
			select d from ArcVersionEntity d join fetch d.version v
			where d.sagaContentId = :sagaContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.number""")
	List<ArcVersionEntity> findOnlineLinkedTo(UUID sagaContentId, Pageable pageable);

	@Override
	@Query(value = """
			select exists (select 1 from arc_version d join content_version v on v.id = d.version_id
			               where d.number_slug = :slug and v.content_id <> :contentId)
			    or exists (select 1 from content_slug s
			               where s.entity_type = 'ARC' and s.slug = :slug and s.content_id <> :contentId)""",
			nativeQuery = true)
	boolean slugIsTakenByAnother(String slug, UUID contentId);

	@Override
	@Modifying
	@Query(value = """
			insert into content_slug (entity_type, slug, content_id, assigned_at)
			select c.entity_type, d.number_slug, c.id, :assignedAt
			from arc_version d
			    join content_version v on v.id = d.version_id
			    join content c on c.id = v.content_id
			where d.version_id = :versionId and d.number_slug is not null
			on conflict (entity_type, slug) do update set assigned_at = excluded.assigned_at
			where content_slug.content_id = excluded.content_id""", nativeQuery = true)
	int assignSlug(UUID versionId, Instant assignedAt);

	/**
	 * How many arcs are online with a paragraph naming this chapter or episode (workflow
	 * 3.7.4).
	 */
	@Query("""
			select count(d) from ArcVersionEntity d join d.version v
			where v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
				and exists (select 1 from ArcParagraphEntity p where p member of d.paragraphs
						and (p.window.discoveredChapterId = :storyContentId or p.window.discoveredEpisodeId = :storyContentId
							or p.window.endedChapterId = :storyContentId or p.window.endedEpisodeId = :storyContentId))""")
	long countOnlineWithPointIn(UUID storyContentId);

	/**
	 * The arcs online with a paragraph naming this chapter or episode, by number: as many
	 * as the page asks for.
	 */
	@Query("""
			select d from ArcVersionEntity d join fetch d.version v
			where v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
				and exists (select 1 from ArcParagraphEntity p where p member of d.paragraphs
						and (p.window.discoveredChapterId = :storyContentId or p.window.discoveredEpisodeId = :storyContentId
							or p.window.endedChapterId = :storyContentId or p.window.endedEpisodeId = :storyContentId))
			order by d.number""")
	List<ArcVersionEntity> findOnlineWithPointIn(UUID storyContentId, Pageable pageable);

	/** The paragraphs count too: a language they are written in is in use. */
	@Override
	@Query(value = """
			select exists (select 1 from arc_version_translation where language_code = :languageCode)
			    or exists (select 1 from arc_version_paragraph_translation
			               where language_code = :languageCode)""", nativeQuery = true)
	boolean existsByLanguage(String languageCode);

}
