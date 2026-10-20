package dev.onepieceapi.contentservice.domain.bounty;

import java.util.List;
import java.util.UUID;

/**
 * The bounties a bounty is chained with - the ones it replaces, one after the other, and
 * the one replacing it - as one caller sees them (implementation plan of the Bounty, D3).
 *
 * @param contentIds the whole chain, newest first, the bounty itself included
 * @param next the bounty replacing it; null when none does
 */
public record BountyChain(List<UUID> contentIds, UUID next) {

}
