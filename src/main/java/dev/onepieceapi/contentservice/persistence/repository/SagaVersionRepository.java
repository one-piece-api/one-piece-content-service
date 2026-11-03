package dev.onepieceapi.contentservice.persistence.repository;

import org.springframework.data.domain.Pageable;
import java.util.List;
import dev.onepieceapi.contentservice.persistence.entity.SagaVersionEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

/**
 * The versions of sagas, content and workflow together. Every query common to all
 * entities comes from {@link VersionBodyRepository}; here, only those reading the
 * entity's own table by name.
 */
public interface SagaVersionRepository extends VersionBodyRepository<SagaVersionEntity> {

	@Override
	default String naturalKeyAttribute() {
		return SagaVersionEntity.Fields.number;
	}

	@Override
	@Query(value = """
			select exists (select 1 from saga_version d join content_version v on v.id = d.version_id
			               where d.number_slug = :slug and v.content_id <> :contentId)
			    or exists (select 1 from content_slug s
			               where s.entity_type = 'SAGA' and s.slug = :slug and s.content_id <> :contentId)""",
			nativeQuery = true)
	boolean slugIsTakenByAnother(String slug, UUID contentId);

	@Override
	@Modifying
	@Query(value = """
			insert into content_slug (entity_type, slug, content_id, assigned_at)
			select c.entity_type, d.number_slug, c.id, :assignedAt
			from saga_version d
			    join content_version v on v.id = d.version_id
			    join content c on c.id = v.content_id
			where d.version_id = :versionId and d.number_slug is not null
			on conflict (entity_type, slug) do update set assigned_at = excluded.assigned_at
			where content_slug.content_id = excluded.content_id""", nativeQuery = true)
	int assignSlug(UUID versionId, Instant assignedAt);

	/**
	 * How many sagas are online with a paragraph naming this chapter or episode (workflow
	 * 3.7.4).
	 */
	@Query("""
			select count(d) from SagaVersionEntity d join d.version v
			where v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
				and exists (select 1 from SagaParagraphEntity p where p member of d.paragraphs
						and (p.window.discoveredChapterId = :storyContentId or p.window.discoveredEpisodeId = :storyContentId
							or p.window.endedChapterId = :storyContentId or p.window.endedEpisodeId = :storyContentId))""")
	long countOnlineWithPointIn(UUID storyContentId);

	/**
	 * The sagas online with a paragraph naming this chapter or episode, by number: as
	 * many as the page asks for.
	 */
	@Query("""
			select d from SagaVersionEntity d join fetch d.version v
			where v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
				and exists (select 1 from SagaParagraphEntity p where p member of d.paragraphs
						and (p.window.discoveredChapterId = :storyContentId or p.window.discoveredEpisodeId = :storyContentId
							or p.window.endedChapterId = :storyContentId or p.window.endedEpisodeId = :storyContentId))
			order by d.number""")
	List<SagaVersionEntity> findOnlineWithPointIn(UUID storyContentId, Pageable pageable);

	/** The paragraphs count too: a language they are written in is in use. */
	@Override
	@Query(value = """
			select exists (select 1 from saga_version_translation where language_code = :languageCode)
			    or exists (select 1 from saga_version_paragraph_translation
			               where language_code = :languageCode)""", nativeQuery = true)
	boolean existsByLanguage(String languageCode);

	/**
	 * The first chapter online of the saga, as published.saga_chapter_span counts it:
	 * where the manga reader meets it (workflow 3.8); null while it has none.
	 */
	@Query(value = "select first_number from published.saga_chapter_span where saga_id = :contentId",
			nativeQuery = true)
	Integer firstChapterOnline(UUID contentId);

	/** The same on the anime axis: its first episode online; null while it has none. */
	@Query(value = "select first_number from published.saga_episode_span where saga_id = :contentId",
			nativeQuery = true)
	Integer firstEpisodeOnline(UUID contentId);

}
