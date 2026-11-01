package dev.onepieceapi.contentservice.domain.readertime;

import java.util.List;
import java.util.Map;

/** Paragraph sections for tests: one paragraph, its texts and where it is discovered. */
public final class ParagraphFixtures {

	private ParagraphFixtures() {
	}

	/** One paragraph without a window. */
	public static List<Paragraph> paragraph(Map<String, String> text) {
		return List.of(new Paragraph(text, StoryWindow.NONE));
	}

	/** One paragraph in one language, without a window. */
	public static List<Paragraph> paragraph(String language, String text) {
		return paragraph(Map.of(language, text));
	}

	/**
	 * One paragraph discovered at this point: the one that opens an item appearing there.
	 */
	public static List<Paragraph> discoveredAt(StoryPoint point, Map<String, String> text) {
		return List.of(new Paragraph(text, StoryWindow.from(point)));
	}

}
