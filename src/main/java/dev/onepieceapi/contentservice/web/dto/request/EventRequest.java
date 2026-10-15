package dev.onepieceapi.contentservice.web.dto.request;

import jakarta.validation.Valid;

import java.util.Map;

/**
 * What a draft of an event is saved with - everything the version says, in place of what
 * it said. Any part may be missing: a draft is saved incomplete. Whether the chapters and
 * the fruits may be linked, and whether the times are in order, is for the service to
 * say.
 *
 * @param occursAt when it happens, required for review
 * @param endsAt when it ends, for a long event; left out for none
 * @param revealedAt where the reader learns of it; left out for the chapter it occurs in
 * @param participants what takes part in it; left out for nothing
 * @param translations title (required for review) and description (optional) per language
 * code; a language left out, or with nothing written in it, has no translation
 */
public record EventRequest(@Valid EventTimeRequest occursAt, @Valid EventTimeRequest endsAt,
		@Valid RevealedAtRequest revealedAt, @Valid EventParticipantsRequest participants,
		Map<String, @Valid StoryTranslationRequest> translations) {

}
