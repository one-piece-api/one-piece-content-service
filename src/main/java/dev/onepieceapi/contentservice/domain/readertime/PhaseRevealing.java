package dev.onepieceapi.contentservice.domain.readertime;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The capability of what the reader learns about in steps (implementation plan of the
 * timeline, T9; ADR-0010): its main fields are the truth, and an ordered list of
 * {@link KnowledgePhase}s says what was believed before each reveal. Part of the version.
 * Enabled on the Devil Fruit only.
 */
public interface PhaseRevealing extends FirstAppearing {

	/** In the order of the story; empty for none. */
	List<? extends KnowledgePhase> phases();

	/** The chapters the phases are superseded at, in their order, those chosen only. */
	default List<UUID> phaseChapterIds() {
		return phases().stream().map(KnowledgePhase::supersededAtChapterId).filter(Objects::nonNull).toList();
	}

	/** The chapters of the first appearance and of the phases, those chosen only. */
	default List<UUID> chapterIds() {
		return Stream.concat(Stream.ofNullable(firstAppearanceChapterId()), phaseChapterIds().stream()).toList();
	}

}
