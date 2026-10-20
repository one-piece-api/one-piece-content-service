package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.web.dto.request.FirstAppearanceRequest;
import dev.onepieceapi.contentservice.web.dto.response.FirstAppearanceResponse;
import dev.onepieceapi.contentservice.web.dto.response.NumberedReferenceResponse;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.UUID;

/**
 * Between a first appearance (ADR-0008) and the wire, for whatever entity has one. The
 * chapter and the episode it points to are given already looked up, so the mapper reads
 * no database.
 */
@UtilityClass
public class FirstAppearanceMapper {

	/** What the request says; left out is none. */
	public StoryPoint toDomain(FirstAppearanceRequest request) {
		return request == null ? StoryPoint.NONE : new StoryPoint(request.chapter(), request.episode());
	}

	/**
	 * The first appearance with its chapter and its episode as they are today; null for
	 * none.
	 * @param storyItems the chapters and the episodes by content id
	 */
	public FirstAppearanceResponse toResponse(StoryPoint point, Map<UUID, NumberedReference> storyItems) {
		if (point.isMissing()) {
			return null;
		}
		return new FirstAppearanceResponse(referenceOf(point.chapterContentId(), storyItems),
				referenceOf(point.episodeContentId(), storyItems));
	}

	private static NumberedReferenceResponse referenceOf(UUID contentId, Map<UUID, NumberedReference> storyItems) {
		return contentId == null ? null : StoryResponseMapper.toReferenceResponse(contentId, storyItems);
	}

}
