package dev.onepieceapi.contentservice.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Map;

/**
 * What a draft of a saga is saved with - everything the version says, in place of what it
 * said. Any part may be missing: a draft is saved incomplete. Whether the number and the
 * titles are free to use is for the service to say.
 *
 * @param number 0 or more, its public address
 * @param translations title and description per language code; a language left out, or
 * with nothing written in it, has no translation
 */
public record SagaRequest(@PositiveOrZero Integer number, Map<String, @Valid StoryTranslationRequest> translations) {

}
