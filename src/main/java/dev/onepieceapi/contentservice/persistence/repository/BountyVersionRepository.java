package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import dev.onepieceapi.contentservice.domain.bounty.BountyValidity;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.BountyVersionEntity;
import dev.onepieceapi.contentservice.persistence.projection.HolderBountyCount;
import dev.onepieceapi.contentservice.persistence.specification.BountySpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The versions of bounties, content and workflow together. Every query common to all
 * entities comes from {@link VersionBodyRepository}; here, those reading the entity's own
 * table by name, and the ones about the chain of bounties - which replaces which. A
 * bounty has no localized name, and no query about one is ever asked.
 * <p>
 * "Most recent among the statuses" is what a list row shows, and what a caller sees of a
 * bounty: a replacing bounty counts through it, as {@code countByTypes} does for fruits.
 */
public interface BountyVersionRepository extends VersionBodyRepository<BountyVersionEntity> {

	/** The key text, which the database writes from the poster's name and the amount. */
	@Override
	default String naturalKeyAttribute() {
		return BountyVersionEntity.Fields.naturalKey;
	}

	@Override
	default boolean hasLocalizedNames() {
		return false;
	}

	/**
	 * No bounty has a name, so none is taken. Without this the generic query, which reads
	 * a {@code name} the translations do not have, would not even start.
	 */
	@Override
	default boolean nameIsTakenByAnother(String languageCode, String name, UUID contentId) {
		return false;
	}

	/** The condition, the validity and the holder a list can be narrowed by. */
	@Override
	default Specification<BountyVersionEntity> attributeConditions(ContentFilter filter,
			Collection<VersionStatus> statuses) {
		Specification<BountyVersionEntity> conditions = Specification.allOf();
		if (filter.attribute(Bounty.CONDITION_ATTRIBUTE) instanceof BountyCondition condition) {
			conditions = conditions.and(BountySpecifications.withCondition(condition));
		}
		if (filter.attribute(Bounty.HOLDER_ATTRIBUTE) instanceof UUID holder) {
			conditions = conditions.and(BountySpecifications.withHolder(holder));
		}
		if (filter.attribute(Bounty.VALIDITY_ATTRIBUTE) instanceof BountyValidity validity) {
			conditions = conditions.and(BountySpecifications.withValidity(validity, statuses));
		}
		return conditions;
	}

	/** Whether a version in one of these statuses has this image. */
	@Query("""
			select count(d) > 0 from BountyVersionEntity d join d.version v
			where d.imageId = :imageId and v.status in :statuses""")
	boolean usesImage(String imageId, Collection<VersionStatus> statuses);

	/**
	 * How many bounties are online first appearing in this chapter or episode (ADR-0008).
	 */
	@Query("""
			select count(d) from BountyVersionEntity d join d.version v
			where (d.firstAppearanceChapterId = :storyContentId or d.firstAppearanceEpisodeId = :storyContentId)
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineFirstAppearingIn(UUID storyContentId);

	/**
	 * The bounties online first appearing in this chapter or episode, by key: as many as
	 * the page asks for.
	 */
	@Query("""
			select d from BountyVersionEntity d join fetch d.version v
			where (d.firstAppearanceChapterId = :storyContentId or d.firstAppearanceEpisodeId = :storyContentId)
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.naturalKey""")
	List<BountyVersionEntity> findOnlineFirstAppearingIn(UUID storyContentId, Pageable pageable);

	/** How many bounties are online replacing this one. */
	@Query("""
			select count(d) from BountyVersionEntity d join d.version v
			where d.previousContentId = :previousContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineReplacing(UUID previousContentId);

	/** The bounties online replacing this one, by key: as many as the page asks for. */
	@Query("""
			select d from BountyVersionEntity d join fetch d.version v
			where d.previousContentId = :previousContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.naturalKey""")
	List<BountyVersionEntity> findOnlineReplacing(UUID previousContentId, Pageable pageable);

	/** How many bounties are online held by this character. */
	@Query("""
			select count(d) from BountyVersionEntity d join d.version v
			where d.holderContentId = :holderContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineWithHolder(UUID holderContentId);

	/**
	 * The bounties online held by this character, by key: as many as the page asks for.
	 */
	@Query("""
			select d from BountyVersionEntity d join fetch d.version v
			where d.holderContentId = :holderContentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by d.naturalKey""")
	List<BountyVersionEntity> findOnlineWithHolder(UUID holderContentId, Pageable pageable);

	/**
	 * How many bounties each of these characters holds for a caller seeing these
	 * statuses: the bounties whose most recent visible version names it - the version
	 * their list shows (see {@code VersionBodySpecifications#mostRecentIn}).
	 */
	@Query("""
			select new dev.onepieceapi.contentservice.persistence.projection.HolderBountyCount(
				d.holderContentId, count(d))
			from BountyVersionEntity d join d.version v
			where d.holderContentId in :holderContentIds and v.status in :statuses
				and v.versionNumber = (select max(o.versionNumber) from ContentVersionEntity o
					where o.contentId = v.contentId and o.status in :statuses)
			group by d.holderContentId""")
	List<HolderBountyCount> countByHolders(Collection<UUID> holderContentIds, Collection<VersionStatus> statuses);

	/**
	 * The bounties online, other than this content, replacing the same one: at most one,
	 * when the rules hold - as many as the page asks for.
	 */
	@Query("""
			select d from BountyVersionEntity d join fetch d.version v
			where d.previousContentId = :previousContentId and v.contentId <> :contentId
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	List<BountyVersionEntity> findOnlineOthersReplacing(UUID previousContentId, UUID contentId, Pageable pageable);

	/**
	 * Whether a version, in any status, of another content has this previous: a bounty is
	 * replaced by at most one.
	 */
	@Query("""
			select count(d) > 0 from BountyVersionEntity d join d.version v
			where d.previousContentId = :previousContentId and v.contentId <> :contentId""")
	boolean existsOtherWithPrevious(UUID previousContentId, UUID contentId);

	/**
	 * The previous of the most recent version of this content among the statuses; empty
	 * for none, or for a content with no such version.
	 */
	@Query("""
			select d.previousContentId from BountyVersionEntity d join d.version v
			where v.contentId = :contentId and v.status in :statuses
				and v.versionNumber = (select max(o.versionNumber) from ContentVersionEntity o
					where o.contentId = :contentId and o.status in :statuses)""")
	Optional<UUID> findPreviousOfMostRecent(UUID contentId, Collection<VersionStatus> statuses);

	/**
	 * The contents, among these, that another bounty replaces for a caller seeing these
	 * statuses: those named by the most recent visible version of a bounty.
	 */
	@Query("""
			select distinct d.previousContentId from BountyVersionEntity d join d.version v
			where d.previousContentId in :contentIds and v.status in :statuses
				and v.versionNumber = (select max(o.versionNumber) from ContentVersionEntity o
					where o.contentId = v.contentId and o.status in :statuses)""")
	List<UUID> findReplaced(Collection<UUID> contentIds, Collection<VersionStatus> statuses);

	/**
	 * The contents whose most recent version among the statuses replaces this one: at
	 * most one is expected, but drafts are not checked against each other.
	 */
	@Query("""
			select v.contentId from BountyVersionEntity d join d.version v
			where d.previousContentId = :previousContentId and v.status in :statuses
				and v.versionNumber = (select max(o.versionNumber) from ContentVersionEntity o
					where o.contentId = v.contentId and o.status in :statuses)""")
	List<UUID> findReplacing(UUID previousContentId, Collection<VersionStatus> statuses);

	@Override
	@Query(value = """
			select exists (select 1 from bounty_version d join content_version v on v.id = d.version_id
			               where d.key_slug = :slug and v.content_id <> :contentId)
			    or exists (select 1 from content_slug s
			               where s.entity_type = 'BOUNTY' and s.slug = :slug and s.content_id <> :contentId)""",
			nativeQuery = true)
	boolean slugIsTakenByAnother(String slug, UUID contentId);

	@Override
	@Modifying
	@Query(value = """
			insert into content_slug (entity_type, slug, content_id, assigned_at)
			select c.entity_type, d.key_slug, c.id, :assignedAt
			from bounty_version d
			    join content_version v on v.id = d.version_id
			    join content c on c.id = v.content_id
			where d.version_id = :versionId and d.key_slug is not null
			on conflict (entity_type, slug) do update set assigned_at = excluded.assigned_at
			where content_slug.content_id = excluded.content_id""", nativeQuery = true)
	int assignSlug(UUID versionId, Instant assignedAt);

}
