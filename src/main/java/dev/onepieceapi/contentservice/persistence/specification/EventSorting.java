package dev.onepieceapi.contentservice.persistence.specification;

import dev.onepieceapi.contentservice.domain.event.EventSortField;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ChapterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.EventVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import dev.onepieceapi.contentservice.persistence.entity.WorldDateEmbeddable;
import dev.onepieceapi.contentservice.persistence.specification.VersionBodySorting.OwnSorting;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

/**
 * The sort only events have: by when they occur, in the order of the world - every world
 * date first, by its first year, then every chapter, by the number of its version last
 * approved, as a relation to it is shown. An event that does not say yet when it occurs
 * comes last.
 */
public final class EventSorting {

	public static final OwnSorting<EventVersionEntity> SORTING = new OwnSorting<>(
			Map.of(EventSortField.OCCURS_AT.field(), EventSorting::worldOrderOf),
			Sort.by(Sort.Direction.ASC, EventSortField.OCCURS_AT.field()));

	/** Where the time is a world date, a chapter or none: the first key of the order. */
	private static final int WORLD_DATE_RANK = 0;

	private static final int CHAPTER_RANK = 1;

	private EventSorting() {
	}

	private static List<Expression<?>> worldOrderOf(Root<EventVersionEntity> root, CriteriaQuery<?> query,
			CriteriaBuilder cb) {
		Expression<Integer> yearFrom = root.get(EventVersionEntity.Fields.occursAtWorldDate)
			.get(WorldDateEmbeddable.Fields.yearFrom);
		Expression<Integer> rank = cb.<Integer>selectCase()
			.when(cb.isNotNull(yearFrom), WORLD_DATE_RANK)
			.when(cb.isNotNull(root.get(EventVersionEntity.Fields.occursAtChapterId)), CHAPTER_RANK)
			.otherwise(cb.nullLiteral(Integer.class));
		return List.of(rank, yearFrom, chapterNumberOf(root, query, cb));
	}

	/** The number of the chapter it occurs in, as its version last approved says. */
	private static Subquery<Integer> chapterNumberOf(Root<EventVersionEntity> root, CriteriaQuery<?> query,
			CriteriaBuilder cb) {
		Subquery<Integer> number = query.subquery(Integer.class);
		Root<ChapterVersionEntity> chapter = number.from(ChapterVersionEntity.class);
		Path<ContentVersionEntity> version = chapter.get(VersionBodyEntity.Fields.version);
		number.select(chapter.get(ChapterVersionEntity.Fields.number))
			.where(cb.equal(version.get(ContentVersionEntity.Fields.contentId),
					root.get(EventVersionEntity.Fields.occursAtChapterId)),
					ContentVersionSpecifications.mostRecentIn(version, VersionStatus.approved(), query, cb));
		return number;
	}

}
