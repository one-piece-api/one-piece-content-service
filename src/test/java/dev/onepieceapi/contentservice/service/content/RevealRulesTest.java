package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReveal;
import dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals;
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

import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.CHAPTER_1;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.CHAPTER_100;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.CHAPTER_1044;
import static dev.onepieceapi.contentservice.domain.devilfruit.GomuGomuReveals.NUMBERS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules every reveal follows (implementation plan of the timeline, T9; ADR-0010): a
 * field at least, a chapter, and the order of the story - after the first appearance and
 * after the reveal before - on save and again going online.
 */
class RevealRulesTest {

	private static final String CHAPTER_FIELD = "reveals[%d].revealedAt.chapter";

	private static DevilFruitReveal reveal(UUID chapter) {
		return new DevilFruitReveal(new StoryPoint(chapter), "Gomu Gomu", null, null, Map.of());
	}

	private static DevilFruit fruit(UUID firstAppearance, DevilFruitReveal... reveals) {
		return new DevilFruit("Hito Hito", null, null, Map.of(), null, new StoryPoint(firstAppearance),
				List.of(reveals));
	}

	@Test
	void revealsInTheOrderOfTheStoryAfterTheFirstAppearanceAreAccepted() {
		assertThat(RevealRules.outOfOrder(GomuGomuReveals.fruit(), NUMBERS)).isEmpty();
		assertThat(RevealRules.incomplete(GomuGomuReveals.fruit())).isEmpty();
	}

	@Test
	void aRevealRevealedAtOrBeforeTheFirstAppearanceIsRefused() {
		var violations = RevealRules.outOfOrder(fruit(CHAPTER_100, reveal(CHAPTER_1), reveal(CHAPTER_100)), NUMBERS);

		assertThat(violations).containsExactly(
				new FieldViolation(CHAPTER_FIELD.formatted(0), "must come after the first appearance"),
				new FieldViolation(CHAPTER_FIELD.formatted(1), "must come after the first appearance"));
	}

	@Test
	void aRevealNotAfterThePreviousOneIsRefused() {
		var violations = RevealRules
			.outOfOrder(fruit(CHAPTER_1, reveal(CHAPTER_1044), reveal(CHAPTER_100), reveal(CHAPTER_100)), NUMBERS);

		assertThat(violations).containsExactly(
				new FieldViolation(CHAPTER_FIELD.formatted(1), "must come after the previous reveal"),
				new FieldViolation(CHAPTER_FIELD.formatted(2), "must come after the previous reveal"));
	}

	@Test
	void withoutAFirstAppearanceOnlyTheOrderOfTheRevealsCounts() {
		assertThat(RevealRules.outOfOrder(fruit(null, reveal(CHAPTER_1), reveal(CHAPTER_100)), NUMBERS)).isEmpty();
	}

	@Test
	void aChapterWithNoKnownNumberIsLeftOutOfTheComparison() {
		UUID unknown = UUID.randomUUID();

		assertThat(RevealRules.outOfOrder(fruit(CHAPTER_100, reveal(unknown), reveal(CHAPTER_1044)), NUMBERS))
			.isEmpty();
		assertThat(RevealRules.outOfOrder(fruit(unknown, reveal(CHAPTER_1)), NUMBERS)).isEmpty();
	}

	@Test
	void aRevealWithoutAFieldOrAChapterIsNamed() {
		var empty = new DevilFruitReveal(new StoryPoint(CHAPTER_100), null, null, null, Map.of());
		var noChapter = new DevilFruitReveal(StoryPoint.NONE, "Gomu Gomu", null, null, Map.of());

		assertThat(RevealRules.incomplete(fruit(CHAPTER_1, empty, noChapter))).containsExactly(
				new FieldViolation("reveals[0]", "must define at least one field"),
				new FieldViolation(CHAPTER_FIELD.formatted(1), "is required"));
	}

	@ParameterizedTest
	@EnumSource(value = VersionAction.class, names = { "PUBLISH", "RESTORE" })
	void goingOnlineOutOfOrderIsBlockedNamingTheFirstReveal(VersionAction action) {
		var renumbered = Map.of(CHAPTER_1, 1, CHAPTER_100, 2000, CHAPTER_1044, 1044);

		assertThat(RevealRules.outOfOrderOnline(action, GomuGomuReveals.fruit(), renumbered))
			.contains(new ActionBlock(BlockReason.REVEALS_OUT_OF_ORDER, Map.of("reveal", 1)));
		assertThat(RevealRules.outOfOrderOnline(action, GomuGomuReveals.fruit(), NUMBERS)).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = VersionAction.class, names = { "SUBMIT", "APPROVE", "RETIRE", "ARCHIVE" })
	void otherActionsDoNotLookAtTheOrder(VersionAction action) {
		var renumbered = Map.of(CHAPTER_1, 1, CHAPTER_100, 2000, CHAPTER_1044, 1044);

		assertThat(RevealRules.outOfOrderOnline(action, GomuGomuReveals.fruit(), renumbered)).isEmpty();
	}

}
