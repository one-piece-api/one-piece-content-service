package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.readertime.KnowledgePhase;
import dev.onepieceapi.contentservice.domain.readertime.PhaseRevealing;
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
 * The rules of the knowledge phases (docs/user-flows/content-editorial-workflow.md 3.6;
 * implementation plan of the timeline, T9; ADR-0010), written once for whatever is
 * {@link PhaseRevealing}: each phase holds a field at least, and the phases follow the
 * story - each superseded at a chapter after the first appearance and after the one
 * before. Checked on every save against the chapters as last approved, and again going
 * online against the chapters online, since a chapter may be renumbered in between. The
 * chapter of a phase is a relation like any other ({@link #relation}). Which fields a
 * phase holds, and what they must say, is the entity's to add.
 */
@UtilityClass
public class KnowledgePhaseRules {

	/** Where a phase is in the request, e.g. {@code phases[2].}. */
	public static final String PHASE_PREFIX = "phases[%d].";

	private static final String PHASE_FIELD = "phases[%d]";

	private static final String CHAPTER_FIELD = "supersededAt.chapter";

	private static final String EMPTY = "must define at least one field";

	private static final String REQUIRED = "is required";

	private static final String NOT_AFTER_FIRST_APPEARANCE = "must come after the first appearance";

	private static final String NOT_AFTER_PREVIOUS = "must come after the previous phase";

	private static final String NOT_LINKABLE = "must be a Chapter with an approved version";

	private static final String PHASE = "phase";

	/**
	 * The chapter a phase is superseded at as a relation to the chapters.
	 * @param itemsName what the items pointing to a chapter are called when they keep it
	 * from being retired, e.g. {@code fruits}
	 */
	public <T extends KnowledgePhase> RelationDescriptor<T> relation(String itemsName) {
		return RelationDescriptor.<T>builder()
			.name("supersededAtChapter")
			.field(CHAPTER_FIELD)
			.target(KnowledgePhase::supersededAtChapterId)
			.notLinkable(NOT_LINKABLE)
			.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
			.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
			.itemsName(itemsName)
			.build();
	}

	/** Every phase holding no field, and every one without a chapter. */
	public List<FieldViolation> incomplete(PhaseRevealing item) {
		List<FieldViolation> violations = new ArrayList<>();
		List<? extends KnowledgePhase> phases = item.phases();
		for (int index = 0; index < phases.size(); index++) {
			if (phases.get(index).definesNothing()) {
				violations.add(new FieldViolation(PHASE_FIELD.formatted(index), EMPTY));
			}
			if (phases.get(index).supersededAt().isMissing()) {
				violations.add(new FieldViolation(PHASE_PREFIX.formatted(index) + CHAPTER_FIELD, REQUIRED));
			}
		}
		return violations;
	}

	/**
	 * Every phase out of the order of the story. A chapter with no known number - none
	 * chosen, or not linkable, which is told apart - is left out of the comparison.
	 * @param chapterNumbers the number of each chapter by its content
	 */
	public List<FieldViolation> outOfOrder(PhaseRevealing item, Map<UUID, Integer> chapterNumbers) {
		return misplaced(item, chapterNumbers).stream()
			.map(phase -> new FieldViolation(PHASE_PREFIX.formatted(phase.position()) + CHAPTER_FIELD, phase.reason()))
			.toList();
	}

	/**
	 * Refuses an item going online while its phases are out of order as the chapters
	 * online number them, naming the first phase at fault.
	 * @param onlineNumbers the number of each chapter by its content, as online
	 */
	public Optional<ActionBlock> outOfOrderOnline(VersionAction action, PhaseRevealing item,
			Map<UUID, Integer> onlineNumbers) {
		if (!RelationRules.GOING_ONLINE.contains(action)) {
			return Optional.empty();
		}
		return misplaced(item, onlineNumbers).stream()
			.findFirst()
			.map(phase -> new ActionBlock(BlockReason.PHASES_OUT_OF_ORDER, Map.of(PHASE, phase.position())));
	}

	private static List<Misplaced> misplaced(PhaseRevealing item, Map<UUID, Integer> chapterNumbers) {
		List<Misplaced> misplaced = new ArrayList<>();
		Integer firstAppearance = item.firstAppearanceChapterId() == null ? null
				: chapterNumbers.get(item.firstAppearanceChapterId());
		Integer previous = null;
		List<? extends KnowledgePhase> phases = item.phases();
		for (int index = 0; index < phases.size(); index++) {
			UUID chapter = phases.get(index).supersededAtChapterId();
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

	/** A phase out of order, by its position, and why. */
	private record Misplaced(int position, String reason) {
	}

}
