package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.web.dto.request.FirstAppearanceRequest;
import dev.onepieceapi.contentservice.web.dto.response.FirstAppearanceResponse;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.UUID;

/**
 * Between a first appearance (implementation plan of the timeline, T7) and the wire, for
 * whatever entity has one. The chapter it points to is given already looked up, so the
 * mapper reads no database.
 */
@UtilityClass
public class FirstAppearanceMapper {

	/** What the request says; left out is none. */
	public StoryPoint toDomain(FirstAppearanceRequest request) {
		return request == null ? StoryPoint.NONE : new StoryPoint(request.chapter());
	}

	/** The first appearance with its chapter as it is today; null for none. */
	public FirstAppearanceResponse toResponse(StoryPoint point, Map<UUID, NumberedReference> chapters) {
		if (point.isMissing()) {
			return null;
		}
		return new FirstAppearanceResponse(StoryResponseMapper.toReferenceResponse(point.chapterContentId(), chapters));
	}

}
