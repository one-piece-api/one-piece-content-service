package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitPhase;
import dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuPhases;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.exception.web.FieldViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuPhases.CHAPTER_1;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuPhases.CHAPTER_100;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuPhases.CHAPTER_1044;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuPhases.NUMBERS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules every knowledge phase follows (implementation plan of the timeline, T9;
 * ADR-0010): a field at least, a chapter, and the order of the story - after the first
 * appearance and after the phase before - on save and again going online.
 */
class KnowledgePhaseRulesTest {

	private static final String CHAPTER_FIELD = "phases[%d].supersededAt.chapter";

	private static DevilFruitPhase phase(UUID chapter) {
		return new DevilFruitPhase(new StoryPoint(chapter), "Gomu Gomu", null, null, Map.of());
	}

	private static DevilFruit fruit(UUID firstAppearance, DevilFruitPhase... phases) {
		return new DevilFruit("Hito Hito", null, null, Map.of(), null, new StoryPoint(firstAppearance),
				List.of(phases));
	}

	@Test
	void phasesInTheOrderOfTheStoryAfterTheFirstAppearanceAreAccepted() {
		assertThat(KnowledgePhaseRules.outOfOrder(GomuGomuPhases.fruit(), NUMBERS)).isEmpty();
		assertThat(KnowledgePhaseRules.incomplete(GomuGomuPhases.fruit())).isEmpty();
	}

	@Test
	void aPhaseSupersededAtOrBeforeTheFirstAppearanceIsRefused() {
		var violations = KnowledgePhaseRules.outOfOrder(fruit(CHAPTER_100, phase(CHAPTER_1), phase(CHAPTER_100)),
				NUMBERS);

		assertThat(violations).containsExactly(
				new FieldViolation(CHAPTER_FIELD.formatted(0), "must come after the first appearance"),
				new FieldViolation(CHAPTER_FIELD.formatted(1), "must come after the first appearance"));
	}

	@Test
	void aPhaseNotAfterThePreviousOneIsRefused() {
		var violations = KnowledgePhaseRules
			.outOfOrder(fruit(CHAPTER_1, phase(CHAPTER_1044), phase(CHAPTER_100), phase(CHAPTER_100)), NUMBERS);

		assertThat(violations).containsExactly(
				new FieldViolation(CHAPTER_FIELD.formatted(1), "must come after the previous phase"),
				new FieldViolation(CHAPTER_FIELD.formatted(2), "must come after the previous phase"));
	}

	@Test
	void withoutAFirstAppearanceOnlyTheOrderOfThePhasesCounts() {
		assertThat(KnowledgePhaseRules.outOfOrder(fruit(null, phase(CHAPTER_1), phase(CHAPTER_100)), NUMBERS))
			.isEmpty();
	}

	@Test
	void aChapterWithNoKnownNumberIsLeftOutOfTheComparison() {
		UUID unknown = UUID.randomUUID();

		assertThat(KnowledgePhaseRules.outOfOrder(fruit(CHAPTER_100, phase(unknown), phase(CHAPTER_1044)), NUMBERS))
			.isEmpty();
		assertThat(KnowledgePhaseRules.outOfOrder(fruit(unknown, phase(CHAPTER_1)), NUMBERS)).isEmpty();
	}

	@Test
	void aPhaseWithoutAFieldOrAChapterIsNamed() {
		var empty = new DevilFruitPhase(new StoryPoint(CHAPTER_100), null, null, null, Map.of());
		var noChapter = new DevilFruitPhase(StoryPoint.NONE, "Gomu Gomu", null, null, Map.of());

		assertThat(KnowledgePhaseRules.incomplete(fruit(CHAPTER_1, empty, noChapter))).containsExactly(
				new FieldViolation("phases[0]", "must define at least one field"),
				new FieldViolation(CHAPTER_FIELD.formatted(1), "is required"));
	}

	@ParameterizedTest
	@EnumSource(value = VersionAction.class, names = { "PUBLISH", "RESTORE" })
	void goingOnlineOutOfOrderIsBlockedNamingTheFirstPhase(VersionAction action) {
		var renumbered = Map.of(CHAPTER_1, 1, CHAPTER_100, 2000, CHAPTER_1044, 1044);

		assertThat(KnowledgePhaseRules.outOfOrderOnline(action, GomuGomuPhases.fruit(), renumbered))
			.contains(new ActionBlock(BlockReason.PHASES_OUT_OF_ORDER, Map.of("phase", 1)));
		assertThat(KnowledgePhaseRules.outOfOrderOnline(action, GomuGomuPhases.fruit(), NUMBERS)).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = VersionAction.class, names = { "SUBMIT", "APPROVE", "RETIRE", "ARCHIVE" })
	void otherActionsDoNotLookAtTheOrder(VersionAction action) {
		var renumbered = Map.of(CHAPTER_1, 1, CHAPTER_100, 2000, CHAPTER_1044, 1044);

		assertThat(KnowledgePhaseRules.outOfOrderOnline(action, GomuGomuPhases.fruit(), renumbered)).isEmpty();
	}

}
