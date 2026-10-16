package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.readertime.Revealing;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The rules of the reveals (docs/user-flows/content-editorial-workflow.md 3.6;
 * implementation plan of the timeline, T9; ADR-0010), written once for whatever is
 * {@link Revealing}: each reveal holds a field at least, and the reveals follow the story
 * - each revealed at a chapter after the first appearance and after the one before.
 * Checked on every save against the chapters as last approved, and again going online
 * against the chapters online, since a chapter may be renumbered in between. The chapter
 * of a reveal is a relation like any other ({@link #relation}). Which fields a reveal
 * holds, and what they must say, is the entity's to add.
 */
@UtilityClass
public class RevealRules {

	/** Where a reveal is in the request, e.g. {@code reveals[2].}. */
	public static final String REVEAL_PREFIX = "reveals[%d].";

	private static final String REVEAL_FIELD = "reveals[%d]";

	private static final String CHAPTER_FIELD = "revealedAt.chapter";

	private static final String EMPTY = "must define at least one field";

	private static final String REQUIRED = "is required";

	private static final String NOT_AFTER_FIRST_APPEARANCE = "must come after the first appearance";

	private static final String NOT_AFTER_PREVIOUS = "must come after the previous reveal";

	private static final String NOT_LINKABLE = "must be a Chapter with an approved version";

	private static final String REVEAL = "reveal";

	/**
	 * The chapter a reveal is revealed at as a relation to the chapters.
	 * @param itemsName what the items pointing to a chapter are called when they keep it
	 * from being retired, e.g. {@code fruits}
	 */
	public <T extends Reveal> RelationDescriptor<T> relation(String itemsName) {
		return RelationDescriptor.<T>builder()
			.name("revealedAtChapter")
			.field(CHAPTER_FIELD)
			.target(Reveal::revealedAtChapterId)
			.notLinkable(NOT_LINKABLE)
			.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
			.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
			.itemsName(itemsName)
			.build();
	}

	/** Every reveal holding no field, and every one without a chapter. */
	public List<FieldViolation> incomplete(Revealing item) {
		List<FieldViolation> violations = new ArrayList<>();
		List<? extends Reveal> reveals = item.reveals();
		for (int index = 0; index < reveals.size(); index++) {
			if (reveals.get(index).definesNothing()) {
				violations.add(new FieldViolation(REVEAL_FIELD.formatted(index), EMPTY));
			}
			if (reveals.get(index).revealedAt().isMissing()) {
				violations.add(new FieldViolation(REVEAL_PREFIX.formatted(index) + CHAPTER_FIELD, REQUIRED));
			}
		}
		return violations;
	}

	/**
	 * Every reveal out of the order of the story. A chapter with no known number - none
	 * chosen, or not linkable, which is told apart - is left out of the comparison.
	 * @param chapterNumbers the number of each chapter by its content
	 */
	public List<FieldViolation> outOfOrder(Revealing item, Map<UUID, Integer> chapterNumbers) {
		return misplaced(item, chapterNumbers).stream()
			.map(reveal -> new FieldViolation(REVEAL_PREFIX.formatted(reveal.position()) + CHAPTER_FIELD,
					reveal.reason()))
			.toList();
	}

	/**
	 * Refuses an item going online while its reveals are out of order as the chapters
	 * online number them, naming the first reveal at fault.
	 * @param onlineNumbers the number of each chapter by its content, as online
	 */
	public Optional<ActionBlock> outOfOrderOnline(VersionAction action, Revealing item,
			Map<UUID, Integer> onlineNumbers) {
		if (!RelationRules.GOING_ONLINE.contains(action)) {
			return Optional.empty();
		}
		return misplaced(item, onlineNumbers).stream()
			.findFirst()
			.map(reveal -> new ActionBlock(BlockReason.REVEALS_OUT_OF_ORDER, Map.of(REVEAL, reveal.position())));
	}

	private static List<Misplaced> misplaced(Revealing item, Map<UUID, Integer> chapterNumbers) {
		List<Misplaced> misplaced = new ArrayList<>();
		Integer firstAppearance = item.firstAppearanceChapterId() == null ? null
				: chapterNumbers.get(item.firstAppearanceChapterId());
		Integer previous = null;
		List<? extends Reveal> reveals = item.reveals();
		for (int index = 0; index < reveals.size(); index++) {
			UUID chapter = reveals.get(index).revealedAtChapterId();
			Integer number = chapter == null ? null : chapterNumbers.get(chapter);
			if (number == null) {
				continue;
			}
			if (firstAppearance != null && number <= firstAppearance) {
				misplaced.add(new Misplaced(index, NOT_AFTER_FIRST_APPEARANCE));
			}
			else if (previous != null && number <= previous) {
				misplaced.add(new Misplaced(index, NOT_AFTER_PREVIOUS));
			}
			previous = number;
		}
		return misplaced;
	}

	/** A reveal out of order, by its position, and why. */
	private record Misplaced(int position, String reason) {
	}

}
