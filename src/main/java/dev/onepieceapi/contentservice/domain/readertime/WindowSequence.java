package dev.onepieceapi.contentservice.domain.readertime;

import java.util.List;

/**
 * Windows that follow one another, in list order and without overlapping, axis by axis
 * (docs/user-flows/content-editorial-workflow.md 3.4.7): the periods of one membership.
 *
 * @param field the list as a request names it, e.g. {@code affiliations[0].periods}
 * @param windows in list order
 */
public record WindowSequence(String field, List<StoryWindow> windows) {

	public WindowSequence {
		windows = List.copyOf(windows);
	}

	/**
	 * One of the windows as a request names it, e.g. {@code affiliations[0].periods[1]}.
	 */
	public String entryField(int index) {
		return this.field + "[" + index + "]";
	}

}
