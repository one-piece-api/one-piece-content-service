package dev.onepieceapi.contentservice.domain.readertime;

import dev.onepieceapi.readertime.ReaderPosition;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A version as a reader at chapter or episode N will see it
 * (docs/user-flows/content-editorial-workflow.md 3.8, UF-CNT-23; reveals plan R6, D2,
 * D5): the same body, resolved there - revealed fields at N (unknown is null), no initial
 * knowledge nor reveals, only the entries, periods, paragraphs and images visible at N,
 * each point keeping only its part on the axis read, up to N (an entry still holding an
 * {@code ended} is former).
 *
 * @param position where the reader is
 * @param body the resolved body; null when the item is not met yet at N
 * @param mainPortraitImageId the main portrait at N, for an item with a gallery; null for
 * none
 * @param hiddenEntries the entries hidden for a reason the editor may not expect
 * @param namesAt what the reader calls each item it points to whose name is revealed
 * (Devil Fruits, Characters), by content id
 * @param <T> what a version of the entity says
 */
public record Preview<T>(ReaderPosition position, T body, String mainPortraitImageId, List<HiddenEntry> hiddenEntries,
		Map<UUID, RevealedName> namesAt) {

	public Preview {
		hiddenEntries = List.copyOf(hiddenEntries);
		namesAt = Map.copyOf(namesAt);
	}

	/** The item is not met yet at N. */
	public static <T> Preview<T> notVisible(ReaderPosition position) {
		return new Preview<>(position, null, null, List.of(), Map.of());
	}

	/** Whether the reader at N has met the item. */
	public boolean visible() {
		return this.body != null;
	}

}
