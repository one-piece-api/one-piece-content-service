package dev.onepieceapi.contentservice.domain.readertime;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The capability of what the reader learns about in steps (implementation plan of the
 * timeline, T9, T13; ADR-0010): its main fields are the truth, and an ordered list of
 * {@link Reveal}s says what the reader learns at each chapter (T13). Part of the version.
 * Enabled on the Devil Fruit only.
 */
public interface Revealing extends FirstAppearing {

	/** In the order of the story; empty for none. */
	List<? extends Reveal> reveals();

	/** The chapters of the reveals, in their order, those chosen only. */
	default List<UUID> revealChapterIds() {
		return reveals().stream().map(Reveal::revealedAtChapterId).filter(Objects::nonNull).toList();
	}

	/** The chapters of the first appearance and of the reveals, those chosen only. */
	default List<UUID> chapterIds() {
		return Stream.concat(Stream.ofNullable(firstAppearanceChapterId()), revealChapterIds().stream()).toList();
	}

}
