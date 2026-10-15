package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.event.Event;
import dev.onepieceapi.contentservice.domain.event.EventTime;
import dev.onepieceapi.contentservice.domain.event.EventTime.ChapterTime;
import dev.onepieceapi.contentservice.domain.event.EventTime.WorldDateTime;
import dev.onepieceapi.contentservice.domain.event.WorldDate;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.service.event.EventRelations;
import dev.onepieceapi.contentservice.web.dto.request.EventListRequest;
import dev.onepieceapi.contentservice.web.dto.request.EventParticipantsRequest;
import dev.onepieceapi.contentservice.web.dto.request.EventRequest;
import dev.onepieceapi.contentservice.web.dto.request.EventTimeRequest;
import dev.onepieceapi.contentservice.web.dto.request.WorldDateRequest;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * From the requests about events to the domain, as typed: tidying up is left to it. A
 * time has exactly one of its parts once the request is valid ({@code OnePart}).
 */
@UtilityClass
public class EventRequestMapper {

	public Event toDomain(EventRequest request) {
		return Event.builder()
			.occursAt(toDomain(request.occursAt()))
			.endsAt(toDomain(request.endsAt()))
			.revealedAt(request.revealedAt() == null ? StoryPoint.NONE : new StoryPoint(request.revealedAt().chapter()))
			.devilFruitContentIds(devilFruitsOf(request.participants()))
			.translations(StoryRequestMapper.toDomain(request.translations()))
			.build();
	}

	/** The common filters, then the Devil Fruit when the list is narrowed to one. */
	public ContentFilter toFilter(EventListRequest request) {
		var filter = new ContentFilter(request.status(), request.q(), request.author(), request.updatedWithinDays());
		return request.devilFruit() == null ? filter
				: filter.relatedTo(EventRelations.DEVIL_FRUITS.name(), request.devilFruit());
	}

	private static EventTime toDomain(EventTimeRequest time) {
		if (time == null) {
			return null;
		}
		return time.chapter() != null ? new ChapterTime(time.chapter()) : new WorldDateTime(toDomain(time.worldDate()));
	}

	private static WorldDate toDomain(WorldDateRequest date) {
		return new WorldDate(date.yearFrom(), date.yearTo(), Boolean.TRUE.equals(date.approximate()));
	}

	private static List<UUID> devilFruitsOf(EventParticipantsRequest participants) {
		return Optional.ofNullable(participants).map(EventParticipantsRequest::devilFruits).orElseGet(List::of);
	}

}
