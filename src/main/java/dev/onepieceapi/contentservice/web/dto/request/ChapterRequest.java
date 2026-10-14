package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a draft of a chapter is saved with - everything the version says, in place of what
 * it said. Any part may be missing: a draft is saved incomplete. Whether the number is
 * free to use, and whether the arc and the volume may be linked, is for the service to
 * say.
 *
 * @param number 0 or more, its public address
 * @param arc the id of the content of the arc it belongs to; whether it can be chosen is
 * answered by {@code GET /arcs/linkable}
 * @param volume the id of the content of the volume that collects it; left out for none.
 * Whether it can be chosen is answered by {@code GET /volumes/linkable}
 * @param japaneseTitle the title as published in Japan, one for every language; optional
 * @param pageCount 1 or more; optional
 * @param releases the release day per country; optional
 * @param translations title and description per language code; a language left out, or
 * with nothing written in it, has no translation
 */
public record ChapterRequest(@PositiveOrZero Integer number,
		@Schema(description = "The content id of its arc") UUID arc,
		@Schema(description = "The content id of its volume; left out for none") UUID volume,
		@Size(max = StoryTranslation.JAPANESE_TITLE_MAX_LENGTH) String japaneseTitle, @Positive Integer pageCount,
		List<@Valid ReleaseRequest> releases, Map<String, @Valid StoryTranslationRequest> translations) {

}
