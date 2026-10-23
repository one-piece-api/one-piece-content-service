package dev.onepieceapi.contentservice.domain.character;

import java.util.UUID;

/**
 * A relative of a character (implementation plan of the Character, D13). Part of the
 * character's version, in the order it is shown.
 *
 * @param relativeContentId the content id of the relative
 * @param kind what the relative is to this character
 */
public record Relative(UUID relativeContentId, RelativeKind kind) {

}
