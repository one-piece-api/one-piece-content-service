package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.readertime.HiddenReason;

/**
 * An entry a preview at N hides for a reason the editor may not expect.
 *
 * @param path the entry as a request names it, e.g. {@code relatives[0]}
 * @param visibleFrom the chapter or episode, on the axis read, from which its target is
 * met; null when unknown or not relevant
 */
public record HiddenEntryResponse(String path, HiddenReason reason, Integer visibleFrom) {

}
