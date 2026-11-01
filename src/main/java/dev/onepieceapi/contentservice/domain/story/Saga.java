package dev.onepieceapi.contentservice.domain.story;

import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.domain.readertime.Paragraphed;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * What one version of a saga says (implementation plan of the story structure, S1-S3):
 * its number, the natural key shared by every language, a title per language code, and
 * its description as paragraphs (workflow 3.7.3). Its span is derived from its arcs'
 * chapters, never stored (S7).
 *
 * @param number 0 or more, unique among the sagas, gaps allowed; null while not written
 * @param description its paragraphs, in order; none is never null
 */
public record Saga(Integer number, Map<String, TitleTranslation> translations,
		List<Paragraph> description) implements ContentBody<Saga>, Paragraphed {

	public static final String NUMBER_FIELD = "number";

	/** No list is null. */
	public Saga {
		translations = translations == null ? Map.of() : translations;
		description = description == null ? List.of() : List.copyOf(description);
	}

	@Override
	public Saga normalized() {
		return new Saga(this.number, StoryTranslations.normalizedTitles(this.translations),
				Paragraph.normalized(this.description));
	}

	@Override
	public NaturalKey naturalKey() {
		return new NaturalKey(NUMBER_FIELD, this.number);
	}

	@Override
	public String nameField() {
		return StoryTranslations.TITLE;
	}

	@Override
	public Set<String> languages() {
		Set<String> languages = new TreeSet<>(this.translations.keySet());
		languages.addAll(paragraphLanguages());
		return languages;
	}

	/** The titles: what a saga is called. */
	@Override
	public Map<String, String> names() {
		return StoryTranslations.titlesOf(this.translations);
	}

	/**
	 * The number, then a title per language, then the paragraphs and their points - every
	 * one missing.
	 */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.number == null) {
			missing.add(NUMBER_FIELD);
		}
		StoryTranslations.addMissingTitles(this.translations, languages, missing);
		missing.addAll(missingParagraphs(languages));
		missing.addAll(missingDiscovered());
		return missing;
	}

	/** No first appearance: the story structure is the axis itself (3.7.4). */
	@Override
	public Optional<StoryPoint> origin() {
		return Optional.empty();
	}

	@Override
	public Map<ParagraphSection, List<Paragraph>> paragraphs() {
		return Map.of(ParagraphSection.DESCRIPTION, this.description);
	}

}
