package dev.onepieceapi.contentservice.domain.readertime;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** What submitting asks of the paragraphs of an item, written once for every entity. */
class ParagraphedTest {

	private static final StoryPoint FIRST_APPEARANCE = StoryPoint.ofChapter(UUID.randomUUID());

	private static final StoryPoint LATER = StoryPoint.ofChapter(UUID.randomUUID());

	private static final List<String> LANGUAGES = List.of("en", "it");

	@Test
	void eachParagraphMissesTheLanguagesItIsNotWrittenInByItsIndex() {
		var item = new Item(Optional.of(FIRST_APPEARANCE), Map.of(ParagraphSection.DESCRIPTION,
				List.of(paragraph(FIRST_APPEARANCE, "it", "en"), paragraph(LATER, "it"), paragraph(LATER, "en"))));

		assertThat(item.missingParagraphs(LANGUAGES)).containsExactly("description[1].text[en]",
				"description[2].text[it]");
	}

	@Test
	void aSectionOpenedOnlyByALaterParagraphIsMissingAsAWhole() {
		var item = new Item(Optional.of(FIRST_APPEARANCE),
				Map.of(ParagraphSection.DESCRIPTION, List.of(paragraph(LATER, "it", "en"))));

		assertThat(item.missingParagraphs(LANGUAGES)).containsExactly("description");
	}

	@Test
	void withoutAFirstAppearanceAnyParagraphOpensTheSectionButAnEmptySectionIsMissing() {
		Map<ParagraphSection, List<Paragraph>> sections = new LinkedHashMap<>();
		sections.put(ParagraphSection.DESCRIPTION, List.of(paragraph(LATER, "it", "en")));
		sections.put(ParagraphSection.ADVANTAGES, List.of());

		assertThat(new Item(Optional.empty(), sections).missingParagraphs(LANGUAGES)).containsExactly("advantages");
	}

	@Test
	void theWindowsAreNamedSectionBySectionInOrderAndTheLanguagesAreThoseOfEveryParagraph() {
		Map<ParagraphSection, List<Paragraph>> sections = new LinkedHashMap<>();
		sections.put(ParagraphSection.DESCRIPTION, List.of(paragraph(FIRST_APPEARANCE, "it")));
		sections.put(ParagraphSection.ADVANTAGES, List.of(paragraph(FIRST_APPEARANCE, "en"), paragraph(LATER, "fr")));
		var item = new Item(Optional.of(FIRST_APPEARANCE), sections);

		assertThat(item.windows()).extracting(NamedWindow::field)
			.containsExactly("description[0]", "advantages[0]", "advantages[1]");
		assertThat(item.paragraphLanguages()).containsExactly("en", "fr", "it");
		assertThat(item.points()).containsExactly(FIRST_APPEARANCE, FIRST_APPEARANCE, FIRST_APPEARANCE, LATER);
	}

	private static Paragraph paragraph(StoryPoint discovered, String... languages) {
		Map<String, String> text = new LinkedHashMap<>();
		for (String language : languages) {
			text.put(language, "text " + language);
		}
		return new Paragraph(text, StoryWindow.from(discovered));
	}

	private record Item(Optional<StoryPoint> origin,
			Map<ParagraphSection, List<Paragraph>> paragraphs) implements Paragraphed {
	}

}
