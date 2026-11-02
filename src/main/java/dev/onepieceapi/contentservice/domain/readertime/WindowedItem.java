package dev.onepieceapi.contentservice.domain.readertime;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The capability of an item whose entries have windows on the reader axis
 * (docs/user-flows/content-editorial-workflow.md 3.7.2, 3.7.4): the lists of a Character,
 * the paragraphs of every {@link Paragraphed} item. Its rules are written once, for
 * whatever is windowed ({@code ReaderAxisRules}).
 */
public interface WindowedItem {

	/**
	 * The point every window is measured from: the first appearance,
	 * {@link StoryPoint#NONE} while not chosen. Empty for the story structure (Saga,
	 * Arc), which has none: its points are bounded only by the rules on {@code ended},
	 * and need only be linkable, never online (3.7.4).
	 */
	Optional<StoryPoint> origin();

	/** Every window of the item, each with where a request names it, in list order. */
	List<NamedWindow> windows();

	/** The windows that must follow one another, e.g. the periods of each membership. */
	default List<WindowSequence> windowSequences() {
		return List.of();
	}

	/**
	 * The point of each reveal of the item, in list order: none unless it is
	 * {@link Revealing}.
	 */
	default List<NamedReveal> namedReveals() {
		return List.of();
	}

	/**
	 * Every point of the item: its first appearance, then those of its windows and of its
	 * reveals.
	 */
	default List<StoryPoint> points() {
		Stream<StoryPoint> windowPoints = windows().stream().flatMap(named -> named.window().points());
		Stream<StoryPoint> revealPoints = namedReveals().stream().map(NamedReveal::point);
		return Stream.of(origin().stream(), windowPoints, revealPoints)
			.flatMap(Function.identity())
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
