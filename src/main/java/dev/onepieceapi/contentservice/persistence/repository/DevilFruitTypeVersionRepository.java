package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.specification.DevilFruitTypeSorting;
import dev.onepieceapi.contentservice.persistence.specification.DevilFruitTypeVersionSpecifications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Clock;
import java.time.Instant;
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
	 * {@code statuses}, filtered and sorted as asked; a sort by name reads it in
	 * {@code language}.
	 */
	default Page<DevilFruitTypeVersionEntity> search(Collection<VersionStatus> statuses, ContentFilter filter,
			Clock clock, Pageable pageable, String language) {
		var specification = DevilFruitTypeVersionSpecifications.listOf(statuses, filter, clock)
			.and(DevilFruitTypeSorting.orderedBy(pageable.getSort(), language));
		return findAll(specification, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
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
	 * The slug this romaji gives, by the database's one definition of a slug
	 * ({@code V8}): empty when it has no letter or digit.
	 */
	@Query(value = "select slug_of(:romaji)", nativeQuery = true)
	Optional<String> slugOf(String romaji);

	/**
	 * Whether a version of another content, in any status, has this romaji - whatever the
	 * case it is written in.
	 */
	@Query("""
			select count(d) > 0 from DevilFruitTypeVersionEntity d join d.version v
			where lower(d.romaji) = lower(:romaji) and v.contentId <> :contentId""")
	boolean romajiIsTakenByAnother(String romaji, UUID contentId);

	/**
	 * Whether another content has this slug - from the romaji of one of its versions, in
	 * any status, or as a slug it had online. Two romaji differing only in accents or
	 * punctuation give the same slug.
	 */
	@Query(value = """
			select exists (select 1 from devil_fruit_type_version d join content_version v on v.id = d.version_id
			               where d.romaji_slug = :slug and v.content_id <> :contentId)
			    or exists (select 1 from content_slug s
			               where s.entity_type = 'DEVIL_FRUIT_TYPE' and s.slug = :slug and s.content_id <> :contentId)""",
			nativeQuery = true)
	boolean slugIsTakenByAnother(String slug, UUID contentId);

	/**
	 * Gives the content of this version the slug of its romaji, as it goes online: added
	 * to its slug history, or marked as assigned again if it had it before.
	 * @return 0 when the slug belongs to another content, which no other check prevents
	 * only for two saves of the same value at the same instant
	 */
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

	/**
	 * Whether a version of another content, in any status, has this name in this language
	 * - whatever the case it is written in.
	 */
	@Query("""
			select count(d) > 0 from DevilFruitTypeVersionEntity d join d.version v join d.translations t
			where key(t) = :languageCode and lower(t.name) = lower(:name) and v.contentId <> :contentId""")
	boolean nameIsTakenByAnother(String languageCode, String name, UUID contentId);

}
