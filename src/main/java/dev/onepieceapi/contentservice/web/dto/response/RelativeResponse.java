package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.character.RelativeKind;

/**
 * A relative of a Character, its window the same on both sides of the link.
 *
 * @param relative the relative as of today
 * @param kind what the relative is to the character
 * @param discovered where the reader learns it; null while not chosen
 * @param ended where the reader learns it is over; null for none
 */
public record RelativeResponse(CharacterReferenceResponse relative, RelativeKind kind,
		FirstAppearanceResponse discovered, FirstAppearanceResponse ended) {

}
