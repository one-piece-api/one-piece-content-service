package dev.onepieceapi.contentservice.persistence.projection;

import dev.onepieceapi.contentservice.domain.character.RelativeKind;

import java.util.UUID;

/** A link stored by a character online: the character holding it, and the stored kind. */
public record RelativeLink(UUID holderContentId, RelativeKind kind) {

}
