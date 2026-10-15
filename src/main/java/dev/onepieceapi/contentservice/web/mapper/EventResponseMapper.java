package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReference;
import dev.onepieceapi.contentservice.domain.event.Event;
import dev.onepieceapi.contentservice.domain.event.EventTime;
import dev.onepieceapi.contentservice.domain.event.EventTime.ChapterTime;
import dev.onepieceapi.contentservice.domain.event.EventTime.WorldDateTime;
import dev.onepieceapi.contentservice.domain.event.WorldDate;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.EventNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.EventParticipantsResponse;
import dev.onepieceapi.contentservice.web.dto.response.EventResponse;
import dev.onepieceapi.contentservice.web.dto.response.EventTimeResponse;
import dev.onepieceapi.contentservice.web.dto.response.RevealedAtResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.dto.response.WorldDateResponse;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.UUID;

/**
 * From events to their response bodies: what a version says is mapped here, the workflow
 * around it by {@link ContentResponseMapper}. The chapters and fruits it points to are
 * given already looked up - as today, for a whole page at once - so the mapper reads no
 * database. {@code revealedAt} is answered as written: where the chapter it occurs in
 * stands for it is for whoever reads it to say.
 */
@UtilityClass
public class EventResponseMapper {

	public VersionResponse<EventResponse> toVersionResponse(VersionAccess<Event> access,
			Map<UUID, NumberedReference> chapters, Map<UUID, DevilFruitReference> fruits) {
		return ContentResponseMapper.toVersionResponse(access, event -> toResponse(event, chapters, fruits));
	}

	public ContentSummaryResponse<EventNamesResponse> toSummaryResponse(ContentSummary<Event> summary,
			Map<UUID, NumberedReference> chapters) {
		return ContentResponseMapper.toSummaryResponse(summary,
				event -> new EventNamesResponse(event.names(), toResponse(event.occursAt(), chapters)));
	}

	public EventResponse toResponse(Event event, Map<UUID, NumberedReference> chapters,
			Map<UUID, DevilFruitReference> fruits) {
		var participants = new EventParticipantsResponse(
				event.devilFruitContentIds().stream().map(fruit -> toReferenceResponse(fruit, fruits)).toList());
		RevealedAtResponse revealedAt = event.revealedAt().isMissing() ? null : new RevealedAtResponse(
				StoryResponseMapper.toReferenceResponse(event.revealedAtChapterId(), chapters));
		return new EventResponse(toResponse(event.occursAt(), chapters), toResponse(event.endsAt(), chapters),
				revealedAt, participants, StoryResponseMapper.toResponses(event.translations()));
	}

	private static EventTimeResponse toResponse(EventTime time, Map<UUID, NumberedReference> chapters) {
		return switch (time) {
			case null -> null;
			case ChapterTime(UUID chapter) ->
				new EventTimeResponse(StoryResponseMapper.toReferenceResponse(chapter, chapters), null);
			case WorldDateTime(WorldDate date) ->
				new EventTimeResponse(null, new WorldDateResponse(date.yearFrom(), date.yearTo(), date.approximate()));
		};
	}

	/**
	 * The fruit with its romaji and names; with only its id when it has nothing approved
	 * to be called by, which the link makes impossible but a read must not fail for.
	 */
	private static DevilFruitReferenceResponse toReferenceResponse(UUID fruitContentId,
			Map<UUID, DevilFruitReference> fruits) {
		DevilFruitReference fruit = fruits.get(fruitContentId);
		return fruit == null ? new DevilFruitReferenceResponse(fruitContentId, null, Map.of())
				: new DevilFruitReferenceResponse(fruit.id(), fruit.romaji(), fruit.names());
	}

}
