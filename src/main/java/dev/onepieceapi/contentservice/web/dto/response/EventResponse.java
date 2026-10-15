package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a version of an event says - the body of its {@link VersionResponse}.
 *
 * @param occursAt when it happens; null while not written
 * @param endsAt when it ends; null for none
 * @param revealedAt where the reader learns of it, as written; null for none - the
 * chapter it occurs in then stands for it
 * @param participants what takes part in it
 * @param translations title and description per language code
 */
public record EventResponse(EventTimeResponse occursAt, EventTimeResponse endsAt, RevealedAtResponse revealedAt,
		EventParticipantsResponse participants, Map<String, StoryTranslationResponse> translations) {

}
