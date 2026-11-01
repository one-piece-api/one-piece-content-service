package dev.onepieceapi.contentservice.domain.bounty;

import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.readertime.ParagraphFixtures.discoveredAt;
import static dev.onepieceapi.contentservice.domain.readertime.ParagraphFixtures.paragraph;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * How what an editor typed becomes what is stored and compared, and what a bounty needs.
 */
class BountyTest {

	private static final UUID PREVIOUS = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID HOLDER = UUID.fromString("7b3e6d1c-2f48-4a90-b5c7-9d0e1f2a3b44");

	@Test
	void theKeyIsThePostersNameAndTheAmountInPlainDigitsNamingBothFields() {
		var key = Bounty.builder()
			.posterName("Monkey D. Luffy")
			.amount(3_000_000_000L)
			.condition(BountyCondition.DEAD_OR_ALIVE)
			.build()
			.naturalKey();

		assertThat(key.text()).isEqualTo("Monkey D. Luffy 3000000000");
		assertThat(key.field()).isEqualTo("key");
		assertThat(key.fields()).containsExactly("posterName", "amount");
	}

	@ParameterizedTest
	@CsvSource(value = { "NULL,100", "Luffy,NULL", "NULL,NULL" }, nullValues = "NULL")
	void theKeyIsMissingWhileEitherPartIs(String posterName, Long amount) {
		var bounty = Bounty.builder().posterName(posterName).amount(amount).build();

		assertThat(bounty.naturalKey().isMissing()).isTrue();
		assertThat(bounty.naturalKeyText()).isNull();
	}

	@Test
	void anAmountOfZeroIsAnAmount() {
		var bounty = Bounty.builder().posterName("Nobody").amount(0L).build();

		assertThat(bounty.naturalKeyText()).isEqualTo("Nobody 0");
	}

	@Test
	void aBountyHasNoNameAndItsLanguagesAreThoseOfItsParagraphs() {
		var bounty = Bounty.builder().posterName("Luffy").amount(1L).description(paragraph("it", "Taglia")).build();

		assertThat(bounty.names()).isEmpty();
		assertThat(bounty.uniqueNames()).isFalse();
		assertThat(bounty.languages()).containsExactly("it");
	}

	@Test
	void theSpaceAroundATextDoesNotCountAndABlankOneIsMissing() {
		var typed = Bounty.builder()
			.posterName("  Roronoa Zoro ")
			.amount(320_000_000L)
			.condition(BountyCondition.ONLY_ALIVE)
			.inactive(true)
			.previousContentId(PREVIOUS)
			.holderContentId(HOLDER)
			.imageId("img")
			.firstAppearance(StoryPoint.ofChapter(PREVIOUS))
			.description(paragraph(Map.of("it", "  Pirata\n", "en", "  ")))
			.build();

		var normalized = typed.normalized();

		assertThat(normalized.posterName()).isEqualTo("Roronoa Zoro");
		assertThat(normalized.description()).containsExactly(new Paragraph(Map.of("it", "Pirata"), StoryWindow.NONE));
		assertThat(normalized.languages()).containsExactly("it");
		assertThat(normalized.inactive()).isTrue();
		assertThat(normalized.previousContentId()).isEqualTo(PREVIOUS);
		assertThat(normalized.holderContentId()).isEqualTo(HOLDER);
		assertThat(normalized.imageId()).isEqualTo("img");
		assertThat(normalized.firstAppearance()).isEqualTo(StoryPoint.ofChapter(PREVIOUS));
		assertThat(Bounty.builder().posterName("   ").amount(1L).build().normalized().posterName()).isNull();
	}

	@Test
	void whatIsRequiredForReviewIsNamedInOrderBeforeTheDescription() {
		var empty = Bounty.builder().build();

		assertThat(empty.missingFields(List.of("en", "it"))).containsExactly("posterName", "amount", "condition",
				"firstAppearance", "description");
	}

	@Test
	void thePreviousTheHolderTheFlagAndThePosterAreNeverMissing() {
		var chapter = StoryPoint.ofChapter(PREVIOUS);
		var complete = Bounty.builder()
			.posterName("Luffy")
			.amount(1_500_000_000L)
			.condition(BountyCondition.DEAD_OR_ALIVE)
			.firstAppearance(chapter)
			.description(discoveredAt(chapter, Map.of("it", "Taglia")))
			.build();

		assertThat(complete.missingFields(List.of("it"))).isEmpty();
		assertThat(complete.missingFields(List.of("it", "en"))).containsExactly("description[0].text[en]");
	}

	@Test
	void anotherPosterOrFlagOrPreviousOrHolderMakesAnotherVersion() {
		var base = Bounty.builder().posterName("Luffy").amount(1L).condition(BountyCondition.DEAD_OR_ALIVE).build();

		assertThat(base).isEqualTo(base.toBuilder().firstAppearance(StoryPoint.NONE).build())
			.isNotEqualTo(base.withImage("img"))
			.isNotEqualTo(base.toBuilder().inactive(true).build())
			.isNotEqualTo(base.toBuilder().previousContentId(PREVIOUS).build())
			.isNotEqualTo(base.toBuilder().holderContentId(HOLDER).build())
			.isNotEqualTo(base.toBuilder().description(paragraph("it", "Taglia")).build());
	}

}
