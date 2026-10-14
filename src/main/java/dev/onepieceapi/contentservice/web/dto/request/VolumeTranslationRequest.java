package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import jakarta.validation.constraints.Size;

/** The localized field of a volume in one language, as a draft is saved: its title. */
public record VolumeTranslationRequest(@Size(max = StoryTranslation.TITLE_MAX_LENGTH) String title) {

}
