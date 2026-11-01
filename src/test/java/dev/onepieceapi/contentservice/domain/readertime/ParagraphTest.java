package dev.onepieceapi.contentservice.domain.readertime;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** A paragraph as typed, stored and read back. */
class ParagraphTest {

	private static final StoryWindow WINDOW = StoryWindow.from(StoryPoint.ofChapter(UUID.randomUUID()));

	@Test
	void theSpaceAroundATextDoesNotCountAndALanguageLeftBlankIsNotWritten() {
		Map<String, String> typed = new HashMap<>();
		typed.put("it", " Creduto morto\n");
		typed.put("en", " \t");

		var normalized = new Paragraph(typed, WINDOW).normalized();

		assertThat(normalized.text()).containsExactly(entry("it", "Creduto morto"));
		assertThat(normalized.window()).isEqualTo(WINDOW);
		assertThat(normalized.textIn("it")).isEqualTo("Creduto morto");
		assertThat(normalized.textIn("en")).isNull();
	}

	@Test
	void aParagraphWithNothingWrittenIsEmptyAndHasNoWindowUntilChosen() {
		var nothing = new Paragraph(null, null);

		assertThat(nothing.text()).isEmpty();
		assertThat(nothing.isEmpty()).isTrue();
		assertThat(nothing.window()).isEqualTo(StoryWindow.NONE);
		assertThat(new Paragraph(Map.of("it", " "), WINDOW).normalized().isEmpty()).isTrue();
	}

	@Test
	void aSectionIsNormalizedParagraphByParagraphInItsOrder() {
		var section = List.of(new Paragraph(Map.of("it", " primo "), WINDOW),
				new Paragraph(Map.of("it", "secondo "), StoryWindow.NONE));

		assertThat(Paragraph.normalized(section)).containsExactly(new Paragraph(Map.of("it", "primo"), WINDOW),
				new Paragraph(Map.of("it", "secondo"), StoryWindow.NONE));
	}

}
