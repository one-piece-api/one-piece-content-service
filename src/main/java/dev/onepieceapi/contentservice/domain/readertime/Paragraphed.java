package dev.onepieceapi.contentservice.domain.readertime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * The capability of an item whose long texts are lists of paragraphs, each with a window
 * (docs/user-flows/content-editorial-workflow.md 3.7.3): Character, Devil Fruit, Devil
 * Fruit Type, Bounty, Affiliation, Species, Saga, Arc. Its windows follow the rules of
 * every point ({@code ReaderAxisRules}); what it needs to be submitted is written here
 * once.
 */
public interface Paragraphed extends WindowedItem {

	/** A paragraph's text in a language, e.g. {@code description[1].text[en]}. */
	String TEXT_FIELD = "%s.text[%s]";

	/**
	 * Each section of the item with its paragraphs in order, the sections in the order a
	 * request names them.
	 */
	Map<ParagraphSection, List<Paragraph>> paragraphs();

	/** The windows of its paragraphs, unless the item has others too. */
	@Override
	default List<NamedWindow> windows() {
		return paragraphWindows();
	}

	/** The window of each paragraph, section by section, e.g. {@code description[1]}. */
	default List<NamedWindow> paragraphWindows() {
		return paragraphs().entrySet()
			.stream()
			.flatMap(section -> IntStream.range(0, section.getValue().size())
				.mapToObj(index -> new NamedWindow(section.getKey().entryField(index),
						section.getValue().get(index).window())))
			.toList();
	}

	/** Every language a paragraph is written in. */
	default Set<String> paragraphLanguages() {
		Set<String> languages = new TreeSet<>();
		paragraphs().values()
			.forEach(section -> section.forEach(paragraph -> languages.addAll(paragraph.text().keySet())));
		return languages;
	}

	/**
	 * What submitting asks of the paragraphs (3.7.3): each one written in every language,
	 * and in every section one discovered at the first appearance - the same point - so
	 * the reader meeting the item finds it described. An item without a first appearance
	 * (Saga, Arc) needs one paragraph per section, discovered anywhere.
	 */
	default List<String> missingParagraphs(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		paragraphs().forEach((section, paragraphs) -> {
			for (int index = 0; index < paragraphs.size(); index++) {
				Paragraph paragraph = paragraphs.get(index);
				String field = section.entryField(index);
				languages.stream()
					.filter(language -> paragraph.textIn(language) == null)
					.forEach(language -> missing.add(TEXT_FIELD.formatted(field, language)));
			}
			if (paragraphs.stream().noneMatch(this::opensTheItem)) {
				missing.add(section.getField());
			}
		});
		return missing;
	}

	/**
	 * Discovered where the reader meets the item; anywhere, when it has no such point.
	 */
	private boolean opensTheItem(Paragraph paragraph) {
		Optional<StoryPoint> origin = origin();
		StoryPoint discovered = paragraph.window().discovered();
		return origin.isEmpty() || !discovered.isMissing() && discovered.equals(origin.get());
	}

}
