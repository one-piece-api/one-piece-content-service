package dev.onepieceapi.contentservice.domain.readertime;

/**
 * An entry the preview at N hides, and why (UF-CNT-23).
 *
 * @param field the entry as a request names it, e.g. {@code relatives[0]}
 * @param visibleFrom the chapter or episode, on the axis read, from which its target is
 * met; null when unknown or not relevant
 */
public record HiddenEntry(String field, HiddenReason reason, Integer visibleFrom) {

	public static HiddenEntry of(String field, HiddenReason reason) {
		return new HiddenEntry(field, reason, null);
	}

}
