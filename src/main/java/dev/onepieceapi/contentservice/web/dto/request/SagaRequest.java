package dev.onepieceapi.contentservice.web.dto.request;

import java.util.List;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Map;

/**
 * What a draft of a saga is saved with - everything the version says, in place of what it
 * said. Any part may be missing: a draft is saved incomplete. Whether the number and the
 * titles are free to use is for the service to say.
 *
 * @param number 0 or more, its public address
 * @param translations the title per language code; a language left out, or with nothing
 * written in it, has no translation
 * @param description its paragraphs, in order; left out for none
 */
public record SagaRequest(@PositiveOrZero Integer number, Map<String, @Valid TitleTranslationRequest> translations,
		@Size(max = Paragraph.MAX_PER_SECTION) List<@NotNull @Valid ParagraphRequest> description) {

}
