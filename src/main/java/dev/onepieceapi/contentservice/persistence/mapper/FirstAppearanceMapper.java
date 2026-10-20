package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.persistence.entity.FirstAppearanceIds;
import lombok.experimental.UtilityClass;

/** Between a first appearance (ADR-0008) and the columns of a version row. */
@UtilityClass
public class FirstAppearanceMapper {

	public FirstAppearanceIds toIds(StoryPoint point) {
		return new FirstAppearanceIds(point.chapterContentId(), point.episodeContentId());
	}

}
