package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;
import java.util.UUID;

/**
 * A Character as a bounty may point to it: the character as of today, never what it said
 * when it was written.
 *
 * @param id the content id of the character
 * @param romaji null when the character has nothing approved to call it by
 * @param names the name per language code, for the languages that have one
 */
public record CharacterReferenceResponse(UUID id, String romaji, Map<String, String> names) {

}
