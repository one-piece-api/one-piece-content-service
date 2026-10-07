package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import dev.onepieceapi.contentservice.persistence.specification.VersionBodySorting;
import dev.onepieceapi.contentservice.persistence.specification.VersionBodySpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The versions of one entity, content and workflow together - what every entity's
 * repository can be asked, written once: {@code #{#entityName}} stands for the entity of
 * the repository extending it, e.g. {@link DevilFruitTypeVersionRepository}. Only
 * versions of that entity have a row in its table, so no query needs to filter by entity
 * type. Every read takes the statuses the caller may see: visibility is part of the
 * query, not a filter applied afterwards. The two queries that name the entity's table
 * are declared here and written by each repository.
 *
 * @param <E> the entity's version table
 */
@NoRepositoryBean
public interface VersionBodyRepository<E extends VersionBodyEntity>
		extends JpaRepository<E, UUID>, JpaSpecificationExecutor<E> {

	/**
	 * One page of the list: one row per content, by its most recent version among
	 * {@code statuses}, filtered and sorted as asked; a sort by name reads it in
	 * {@code language}.
	 */
	default Page<E> search(Collection<VersionStatus> statuses, ContentFilter filter, Clock clock, Pageable pageable,
			String language) {
		var specification = VersionBodySpecifications.<E>listOf(statuses, filter, clock)
			.and(VersionBodySorting.orderedBy(pageable.getSort(), language));
		return findAll(specification, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
	}

	/**
	 * How many contents the list would show in all, with the same statuses and filters.
	 */
	default long count(Collection<VersionStatus> statuses, ContentFilter filter, Clock clock) {
		return count(VersionBodySpecifications.<E>listOf(statuses, filter, clock));
	}

	/**
	 * For each of the given contents, its most recent version among {@code statuses} -
	 * what it is called where it is listed.
	 */
	default List<E> findMostRecent(Collection<UUID> contentIds, Collection<VersionStatus> statuses) {
		return findAll(VersionBodySpecifications.<E>mostRecentIn(statuses)
			.and(VersionBodySpecifications.ofContents(contentIds)));
	}

	/** The visible versions of one content, oldest first. */
	@Query("""
			select d from #{#entityName} d join fetch d.version v
			where v.contentId = :contentId and v.status in :statuses
			order by v.versionNumber""")
	List<E> findVisible(UUID contentId, Collection<VersionStatus> statuses);

	@Query("""
			select d from #{#entityName} d join fetch d.version v
			where v.contentId = :contentId and v.versionNumber = :versionNumber and v.status in :statuses""")
	Optional<E> findVisible(UUID contentId, int versionNumber, Collection<VersionStatus> statuses);

	/**
	 * The content's online version, if it has one - whether or not the caller sees it.
	 */
	@Query("""
			select d from #{#entityName} d join fetch d.version v
			where v.contentId = :contentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	Optional<E> findOnline(UUID contentId);

	/**
	 * Every version of the content but one, in any status, oldest first - whether or not
	 * the caller sees them.
	 */
	@Query("""
			select d from #{#entityName} d join fetch d.version v
			where v.contentId = :contentId and v.versionNumber <> :versionNumber
			order by v.versionNumber""")
	List<E> findOthers(UUID contentId, int versionNumber);

	@Query("""
			select distinct new dev.onepieceapi.contentservice.domain.security.User(
				v.author.userId, v.author.username, v.author.email)
			from #{#entityName} d join d.version v
			where v.status in :statuses
			order by v.author.username""")
	List<User> findAuthors(Collection<VersionStatus> statuses);

	@Query("""
			select count(d) > 0 from #{#entityName} d join d.translations t
			where key(t) = :languageCode""")
	boolean existsByLanguage(String languageCode);

	/**
	 * The slug this romaji gives, by the database's one definition of a slug
	 * ({@code V8}): empty when it has no letter or digit.
	 */
	@Query(value = "select slug_of(:romaji)", nativeQuery = true)
	Optional<String> slugOf(String romaji);

	/**
	 * Whether a version of another content of this entity, in any status, has this romaji
	 * - whatever the case it is written in.
	 */
	@Query("""
			select count(d) > 0 from #{#entityName} d join d.version v
			where lower(d.romaji) = lower(:romaji) and v.contentId <> :contentId""")
	boolean romajiIsTakenByAnother(String romaji, UUID contentId);

	/**
	 * Whether a version of another content of this entity, in any status, has this name
	 * in this language - whatever the case it is written in.
	 */
	@Query("""
			select count(d) > 0 from #{#entityName} d join d.version v join d.translations t
			where key(t) = :languageCode and lower(t.name) = lower(:name) and v.contentId <> :contentId""")
	boolean nameIsTakenByAnother(String languageCode, String name, UUID contentId);

	/**
	 * Whether another content of this entity has this slug - from the romaji of one of
	 * its versions, in any status, or as a slug it had online. Two romaji differing only
	 * in accents or punctuation give the same slug. Written by each repository: it reads
	 * the entity's table.
	 */
	boolean slugIsTakenByAnother(String slug, UUID contentId);

	/**
	 * Gives the content of this version the slug of its romaji, as it goes online: added
	 * to its slug history, or marked as assigned again if it had it before. Written by
	 * each repository: it reads the entity's table.
	 * @return 0 when the slug belongs to another content, which no other check prevents
	 * only for two saves of the same value at the same instant
	 */
	int assignSlug(UUID versionId, Instant assignedAt);

}
