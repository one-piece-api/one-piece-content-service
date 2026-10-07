package dev.onepieceapi.contentservice.persistence.projection;

import java.util.UUID;

/** How many fruits a type has, as one caller's list shows them. */
public record TypeFruitCount(UUID typeContentId, long count) {

}
