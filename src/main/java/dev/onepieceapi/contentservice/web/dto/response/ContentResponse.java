package dev.onepieceapi.contentservice.web.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * A content and the versions of it the caller may see, oldest first. The same for every
 * kind of content: what a version says is read from the version itself.
 *
 * @param onlineVersionNumber null when nothing is online
 */
public record ContentResponse(UUID id, Integer onlineVersionNumber, List<VersionSummaryResponse> versions) {

}
