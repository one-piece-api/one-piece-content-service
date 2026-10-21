package dev.onepieceapi.contentservice.persistence.projection;

import java.util.UUID;

/** How many bounties a character holds, as one caller's list shows them. */
public record HolderBountyCount(UUID holderContentId, long count) {

}
