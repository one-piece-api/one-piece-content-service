package dev.onepieceapi.contentservice.persistence.projection;

import java.util.UUID;

/** Which version of a content is online - see {@link ContentVersionRepository}. */
public record OnlineVersion(UUID contentId, int versionNumber) {

}
