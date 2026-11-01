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
import java.util.UUID;

/**
 * What one version of an arc says (implementation plan of the story structure, S1-S4):
 * its number, the saga it belongs to, a title per language code, and its description as
 * paragraphs (workflow 3.7.3). The saga is the saga's content, not one of its versions,
 * and is required for review.
 *
 * @param number 0 or more, unique among the arcs, gaps allowed; null while not written
 * @param sagaContentId the content of its saga; null while not chosen
 * @param description its paragraphs, in order; none is never null
 */
public record Arc(Integer number, UUID sagaContentId, Map<String, TitleTranslation> translations,
		List<Paragraph> description) implements ContentBody<Arc>, Paragraphed {

	private static final String SAGA_FIELD = "saga";

	/** No list is null. */
	public Arc {
		translations = translations == null ? Map.of() : translations;
		description = description == null ? List.of() : List.copyOf(description);
	}

	@Override
	public Arc normalized() {
		return new Arc(this.number, this.sagaContentId, StoryTranslations.normalizedTitles(this.translations),
				Paragraph.normalized(this.description));
	}

	@Override
	public NaturalKey naturalKey() {
		return new NaturalKey(Saga.NUMBER_FIELD, this.number);
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

	/** The titles: what an arc is called. */
	@Override
	public Map<String, String> names() {
		return StoryTranslations.titlesOf(this.translations);
	}

	/**
	 * The number and the saga, then a title per language, then the paragraphs and their
	 * points - every one missing.
	 */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.number == null) {
			missing.add(Saga.NUMBER_FIELD);
		}
		if (this.sagaContentId == null) {
			missing.add(SAGA_FIELD);
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
