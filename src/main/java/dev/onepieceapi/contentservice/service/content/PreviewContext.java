package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.readertime.GalleryImage;
import dev.onepieceapi.contentservice.domain.readertime.HiddenEntry;
import dev.onepieceapi.contentservice.domain.readertime.HiddenReason;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.domain.readertime.Preview;
import dev.onepieceapi.contentservice.domain.readertime.RevealableField;
import dev.onepieceapi.contentservice.domain.readertime.RevealedName;
import dev.onepieceapi.contentservice.domain.readertime.RevealedValues;
import dev.onepieceapi.contentservice.domain.readertime.Revealing;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.readertime.Windowed;
import dev.onepieceapi.readertime.Axis;
import dev.onepieceapi.readertime.FieldReveal;
import dev.onepieceapi.readertime.Gallery;
import dev.onepieceapi.readertime.ImageKind;
import dev.onepieceapi.readertime.Paragraphs;
import dev.onepieceapi.readertime.Point;
import dev.onepieceapi.readertime.ReaderPosition;
import dev.onepieceapi.readertime.RevealWindow;
import dev.onepieceapi.readertime.RevealedField;
import dev.onepieceapi.readertime.Visibility;
import dev.onepieceapi.readertime.WindowState;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * One preview at N being resolved (workflow 3.8, UF-CNT-23; reveals plan R6): the rules
 * of the library {@code one-piece-reader-time} applied to this service's points, which
 * name chapters and episodes by content, and the entries hidden for a reason the editor
 * may not expect, collected on the way. The items pointed to are read on their version
 * online (D5); one with none is never seen by the reader.
 */
@RequiredArgsConstructor
public class PreviewContext {

	@Getter
	private final ReaderPosition position;

	private final StoryNumbers numbers;

	/** The first appearance of every item pointed to that is online, by content id. */
	private final Map<UUID, StoryPoint> onlineTargets;

	@Getter
	private final List<HiddenEntry> hiddenEntries = new ArrayList<>();

	/** Whether the reader at N has met an item first appearing there. */
	public boolean hasMet(StoryPoint firstAppearance) {
		return Visibility.itemVisibleAt(this.numbers.pointOf(firstAppearance), this.position);
	}

	/**
	 * The same for a point already in numbers, e.g. the first chapter and episode of an
	 * arc.
	 */
	public boolean hasMet(Point firstAppearance) {
		return Visibility.itemVisibleAt(firstAppearance, this.position);
	}

	/** The point as the reader at N reads it: its part on the axis, if reached. */
	public StoryPoint trimmed(StoryPoint point) {
		if (!this.position.hasReached(this.numbers.pointOf(point))) {
			return StoryPoint.NONE;
		}
		return (this.position.axis() == Axis.CHAPTER) ? StoryPoint.ofChapter(point.chapterContentId())
				: StoryPoint.ofEpisode(point.episodeContentId());
	}

	/** The window as the reader at N reads it: an end after N is not shown. */
	public StoryWindow trimmed(StoryWindow window) {
		return new StoryWindow(trimmed(window.discovered()), trimmed(window.ended()));
	}

	public WindowState stateOf(StoryWindow window) {
		return revealWindowOf(window).stateAt(this.position);
	}

	/**
	 * The paragraphs of a section the reader at N reads: discovered and not ended
	 * (3.7.3). One not seen on the axis is flagged.
	 */
	public List<Paragraph> paragraphs(ParagraphSection section, List<Paragraph> paragraphs) {
		IntStream.range(0, paragraphs.size())
			.filter(index -> isOffAxis(paragraphs.get(index).window()))
			.forEach(index -> this.hiddenEntries
				.add(HiddenEntry.of(section.entryField(index), HiddenReason.NOT_ON_AXIS)));
		List<Placed<Paragraph>> placed = paragraphs.stream().map(this::placed).toList();
		return Paragraphs.visibleAt(placed, this.position)
			.stream()
			.map(Placed::item)
			.map(paragraph -> new Paragraph(paragraph.text(), trimmed(paragraph.window())))
			.toList();
	}

	/**
	 * The main portrait at N (3.5.1): the latest appearance discovered and not ended,
	 * else the latest discovered; a form never.
	 */
	public Optional<GalleryImage> mainPortrait(List<GalleryImage> gallery) {
		List<PlacedImage> placed = gallery.stream()
			.map(image -> new PlacedImage(image, revealWindowOf(image.window())))
			.toList();
		return Gallery.mainPortraitAt(placed, this.position).map(PlacedImage::image);
	}

	/** The preview of a body resolved here, with the entries hidden on the way. */
	public <T> Preview<T> previewOf(T resolved) {
		return previewOf(resolved, null, Map.of());
	}

	/** The same, with a main portrait and what the reader calls the items pointed to. */
	public <T> Preview<T> previewOf(T resolved, String mainPortraitImageId, Map<UUID, RevealedName> namesAt) {
		return new Preview<>(this.position, resolved, mainPortraitImageId, this.hiddenEntries, namesAt);
	}

	/**
	 * The entries of a list the reader at N sees, each with its window trimmed. An entry
	 * not discovered on the axis, or whose target is not met yet, is left out, and
	 * flagged when the editor may not expect it.
	 * @param listField the list as a request names it, e.g. {@code relatives}
	 * @param target what an entry points to; null for an entry pointing to nothing
	 * @param rewindowed the same entry with another window
	 */
	public <E extends Windowed> List<E> entries(List<E> entries, String listField, Function<E, UUID> target,
			BiFunction<E, StoryWindow, E> rewindowed) {
		return IntStream.range(0, entries.size())
			.filter(index -> isShown(entries.get(index), listField + "[" + index + "]", target))
			.mapToObj(entries::get)
			.map(entry -> rewindowed.apply(entry, trimmed(entry.window())))
			.toList();
	}

	/** The same, for entries pointing to nothing. */
	public <E extends Windowed> List<E> entries(List<E> entries, String listField,
			BiFunction<E, StoryWindow, E> rewindowed) {
		return entries(entries, listField, entry -> null, rewindowed);
	}

	/**
	 * Whether the reader at N sees what a single relation points to, e.g. a bounty's
	 * holder: online and met. Flagged under the relation's field otherwise.
	 */
	public boolean seesTarget(UUID target, String field) {
		return target == null || targetHidden(target).map(reason -> hide(field, reason, target)).isEmpty();
	}

	/**
	 * The value of a revealed field at N (3.7.1): the latest reveal reached on the axis,
	 * else the initial knowledge, else unknown. A field no reveal names keeps its current
	 * value.
	 * @param current what the main fields say
	 */
	public <F extends Enum<F> & RevealableField, V extends RevealedValues<F, V>> Optional<V> valueAt(
			Revealing<F, V> item, F field, V current) {
		V initial = item.initialKnowledge().holds(field) ? item.initialKnowledge() : null;
		List<FieldReveal<V>> reveals = item.reveals()
			.stream()
			.filter(reveal -> reveal.names(field))
			.map(reveal -> reveal.values().holds(field)
					? FieldReveal.learned(this.numbers.pointOf(reveal.revealedAt()), reveal.values())
					: FieldReveal.<V>ofCurrentValue(this.numbers.pointOf(reveal.revealedAt())))
			.toList();
		return new RevealedField<>(current, initial, reveals).valueAt(this.position);
	}

	private <E extends Windowed> boolean isShown(E entry, String field, Function<E, UUID> target) {
		StoryWindow window = entry.window();
		if (stateOf(window) == WindowState.HIDDEN) {
			if (isOffAxis(window)) {
				this.hiddenEntries.add(HiddenEntry.of(field, HiddenReason.NOT_ON_AXIS));
			}
			return false;
		}
		return seesTarget(target.apply(entry), field);
	}

	/** Discovered with no part on the axis read: never seen there. */
	private boolean isOffAxis(StoryWindow window) {
		return !this.numbers.pointOf(window.discovered()).has(this.position.axis());
	}

	private Optional<HiddenReason> targetHidden(UUID target) {
		StoryPoint firstAppearance = this.onlineTargets.get(target);
		if (firstAppearance == null) {
			return Optional.of(HiddenReason.TARGET_NOT_ONLINE);
		}
		return hasMet(firstAppearance) ? Optional.empty() : Optional.of(HiddenReason.TARGET_NOT_VISIBLE);
	}

	/** Records why the entry is hidden; answers the reason. */
	private HiddenReason hide(String field, HiddenReason reason, UUID target) {
		Integer visibleFrom = null;
		if (reason == HiddenReason.TARGET_NOT_VISIBLE) {
			OptionalInt part = this.numbers.pointOf(this.onlineTargets.get(target)).on(this.position.axis());
			visibleFrom = part.isPresent() ? part.getAsInt() : null;
		}
		this.hiddenEntries.add(new HiddenEntry(field, reason, visibleFrom));
		return reason;
	}

	private RevealWindow revealWindowOf(StoryWindow window) {
		return this.numbers.windowOf(window);
	}

	private <T extends Windowed> Placed<T> placed(T item) {
		return new Placed<>(item, revealWindowOf(item.window()));
	}

	/** An item of this service with its window in numbers, as the library reads it. */
	private record Placed<T>(T item, RevealWindow window) implements dev.onepieceapi.readertime.Windowed {
	}

	/** A gallery image with its window in numbers, as the library reads it. */
	private record PlacedImage(GalleryImage image,
			RevealWindow window) implements dev.onepieceapi.readertime.GalleryImage {

		@Override
		public ImageKind kind() {
			return this.image.kind();
		}

	}

}
