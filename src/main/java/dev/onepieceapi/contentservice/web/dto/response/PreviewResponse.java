package dev.onepieceapi.contentservice.web.dto.response;

import java.util.List;

/**
 * A version as a reader at chapter or episode N will see it (UF-CNT-23; reveals plan R6,
 * D2): the body in the section's own shape, resolved at N.
 *
 * @param <R> how a version's content is answered, e.g. {@link CharacterResponse}
 * @param visible whether the reader at N has met the item; false with no body
 * @param body the resolved body: revealed fields at N (unknown is null), no
 * {@code initialKnowledge} nor {@code reveals}, only what is visible at N, each point
 * with its part on the axis read; null when not visible
 * @param mainPortrait the main portrait at N, for an item with a gallery; null for none
 * @param hiddenEntries the entries hidden for a reason the editor may not expect
 */
public record PreviewResponse<R>(ReaderPositionResponse asOf, boolean visible, R body, ImageResponse mainPortrait,
		List<HiddenEntryResponse> hiddenEntries) {

}
