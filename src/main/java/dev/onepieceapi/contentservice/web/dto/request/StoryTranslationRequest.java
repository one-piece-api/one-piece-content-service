package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import jakarta.validation.constraints.Size;

/** The localized fields of a saga or an arc in one language, as a draft is saved. */
public record StoryTranslationRequest(@Size(max = StoryTranslation.TITLE_MAX_LENGTH) String title,
		@Size(max = StoryTranslation.DESCRIPTION_MAX_LENGTH) String description) {

}
