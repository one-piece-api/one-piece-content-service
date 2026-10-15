package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.event.Event;
import dev.onepieceapi.contentservice.domain.event.EventTime;
import dev.onepieceapi.contentservice.domain.event.EventTime.ChapterTime;
import dev.onepieceapi.contentservice.domain.event.EventTime.WorldDateTime;
import dev.onepieceapi.contentservice.domain.event.WorldDate;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.EventVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.WorldDateEmbeddable;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

/**
 * Between the rows of an event version - its workflow in the shared table, what it says
 * in the entity's own - and the domain: each time of the world axis is a chapter column
 * and a world date group, at most one of the two written.
 */
@UtilityClass
public class EventVersionMapper {

	public Version<Event> toDomain(EventVersionEntity entity) {
		var body = Event.builder()
			.occursAt(toTime(entity.getOccursAtChapterId(), entity.getOccursAtWorldDate()))
			.endsAt(toTime(entity.getEndsAtChapterId(), entity.getEndsAtWorldDate()))
			.revealedAt(new StoryPoint(entity.getRevealedAtChapterId()))
			.devilFruitContentIds(entity.getDevilFruitContentIds().stream().sorted().toList())
			.translations(StoryVersionMapper.toDomain(entity.getTranslations()))
			.build();
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	/** Makes the version say what was given, in place of what it said, and notes when. */
	public void rewrite(EventVersionEntity entity, Event body, Instant now) {
		entity.rewrite(body.occursAtChapterId(), toEmbeddable(body.occursAt()), body.endsAtChapterId(),
				toEmbeddable(body.endsAt()), body.revealedAtChapterId(), body.devilFruitContentIds(),
				StoryVersionMapper.toEmbeddables(body.translations()));
		entity.touch(now);
	}

	private static EventTime toTime(UUID chapterContentId, WorldDateEmbeddable worldDate) {
		if (chapterContentId != null) {
			return new ChapterTime(chapterContentId);
		}
		if (worldDate == null || worldDate.getYearFrom() == null) {
			return null;
		}
		return new WorldDateTime(new WorldDate(worldDate.getYearFrom(), worldDate.getYearTo(),
				Boolean.TRUE.equals(worldDate.getApproximate())));
	}

	private static WorldDateEmbeddable toEmbeddable(EventTime time) {
		return time instanceof WorldDateTime(WorldDate date)
				? new WorldDateEmbeddable(date.yearFrom(), date.yearTo(), date.approximate()) : null;
	}

}
