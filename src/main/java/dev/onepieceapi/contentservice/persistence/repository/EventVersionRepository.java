package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.persistence.entity.EventVersionEntity;
import dev.onepieceapi.contentservice.persistence.specification.EventSorting;
import dev.onepieceapi.contentservice.persistence.specification.VersionBodySorting;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * The versions of events, content and workflow together. Every query common to all
 * entities comes from {@link VersionBodyRepository} - an event has no natural key, so
 * none of a slug; here, the events online that a Devil Fruit or a chapter is used by.
 */
public interface EventVersionRepository extends VersionBodyRepository<EventVersionEntity> {

	/** By when they occur, in the order of the world - also the default of the list. */
	@Override
	default VersionBodySorting.OwnSorting<EventVersionEntity> ownSorting() {
		return EventSorting.SORTING;
	}

	/** How many events are online with this Devil Fruit taking part. */
	@Query("""
			select count(d) from EventVersionEntity d join d.version v
			where :devilFruitContentId member of d.devilFruitContentIds
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineWithDevilFruit(UUID devilFruitContentId);

	/**
	 * The events online with this Devil Fruit taking part, by content id: as many as the
	 * page asks for.
	 */
	@Query("""
			select d from EventVersionEntity d join fetch d.version v
			where :devilFruitContentId member of d.devilFruitContentIds
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by v.contentId""")
	List<EventVersionEntity> findOnlineWithDevilFruit(UUID devilFruitContentId, Pageable pageable);

	/**
	 * How many events are online using this chapter: occurring or ending in it, or
	 * revealed in it.
	 */
	@Query("""
			select count(d) from EventVersionEntity d join d.version v
			where :chapterContentId in (d.occursAtChapterId, d.endsAtChapterId, d.revealedAtChapterId)
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED""")
	long countOnlineInChapter(UUID chapterContentId);

	/**
	 * The events online using this chapter, by content id: as many as the page asks for.
	 */
	@Query("""
			select d from EventVersionEntity d join fetch d.version v
			where :chapterContentId in (d.occursAtChapterId, d.endsAtChapterId, d.revealedAtChapterId)
				and v.status = dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED
			order by v.contentId""")
	List<EventVersionEntity> findOnlineInChapter(UUID chapterContentId, Pageable pageable);

}
