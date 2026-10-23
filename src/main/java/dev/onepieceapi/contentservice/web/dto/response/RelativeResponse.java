package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.character.RelativeKind;

/**
 * A relative of a Character.
 *
 * @param relative the relative as of today
 * @param kind what the relative is to the character
 */
public record RelativeResponse(CharacterReferenceResponse relative, RelativeKind kind) {

}
