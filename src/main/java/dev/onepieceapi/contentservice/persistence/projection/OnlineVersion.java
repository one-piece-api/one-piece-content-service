package dev.onepieceapi.contentservice.persistence.projection;

import java.util.UUID;

/** Which version of an item is online - see {@link ContentVersionRepository}. */
public record OnlineVersion(UUID itemId, int versionNumber) {

}
