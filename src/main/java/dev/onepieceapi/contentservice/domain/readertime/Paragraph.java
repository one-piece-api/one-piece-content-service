package dev.onepieceapi.contentservice.domain.readertime;

import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.List;
import java.util.Map;

/**
 * One paragraph of a long text (docs/user-flows/content-editorial-workflow.md 3.7.3): a
 * text per language and its window on the reader axis. An ended paragraph is hidden from
 * its end, superseded by a later one ("Sabo is believed dead", ended at Dressrosa). Part
 * of the item's version, in the order of its section.
 *
 * @param text per language code, for the languages it is written in
 */
public record Paragraph(Map<String, String> text, StoryWindow window) implements Windowed {

	public static final int TEXT_MAX_LENGTH = 2000;

	public static final int MAX_PER_SECTION = 50;

	public Paragraph {
		text = text == null ? Map.of() : Map.copyOf(text);
		window = StoryWindow.orNone(window);
	}

	/** Every paragraph of a section without the space around its texts. */
	public static List<Paragraph> normalized(List<Paragraph> paragraphs) {
		return paragraphs.stream().map(Paragraph::normalized).toList();
	}

	/** Every text without the space around it; a language left blank is not written. */
	public Paragraph normalized() {
		return new Paragraph(Text.written(this.text), this.window);
	}

	/** Nothing written in any language: a paragraph a save refuses. */
	public boolean isEmpty() {
		return this.text.isEmpty();
	}

	/** What it says in this language; null when nothing is written in it. */
	public String textIn(String language) {
		return this.text.get(language);
	}

}
