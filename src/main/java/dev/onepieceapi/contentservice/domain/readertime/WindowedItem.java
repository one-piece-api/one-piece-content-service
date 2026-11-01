package dev.onepieceapi.contentservice.domain.readertime;

import java.util.List;
import java.util.stream.Stream;

/**
 * The capability of an item whose entries have windows on the reader axis
 * (docs/user-flows/content-editorial-workflow.md 3.7.2, 3.7.4): today the Character. Its
 * rules are written once, for whatever is windowed ({@code ReaderAxisRules}).
 */
public interface WindowedItem extends FirstAppearing {

	/** Every window of the item, each with where a request names it, in list order. */
	List<NamedWindow> windows();

	/** The windows that must follow one another, e.g. the periods of each membership. */
	default List<WindowSequence> windowSequences() {
		return List.of();
	}

	/** Every point of the item: its first appearance, then those of its windows. */
	default List<StoryPoint> points() {
		return Stream.concat(Stream.of(firstAppearance()), windows().stream().flatMap(named -> named.window().points()))
			.filter(point -> !point.isMissing())
			.toList();
	}

	/**
	 * Each window without a {@code discovered} point, e.g. {@code species[1].discovered}.
	 */
	default List<String> missingDiscovered() {
		return windows().stream()
			.filter(named -> named.window().discovered().isMissing())
			.map(named -> named.pointField(NamedWindow.DISCOVERED))
			.toList();
	}

}
