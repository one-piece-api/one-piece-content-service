package dev.onepieceapi.contentservice.domain.story;

import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What one version of an arc says (implementation plan of the story structure, S1-S4):
 * its number, the saga it belongs to, and a title and description per language code. The
 * saga is the saga's content, not one of its versions, and is required for review.
 *
 * @param number 0 or more, unique among the arcs, gaps allowed; null while not written
 * @param sagaContentId the content of its saga; null while not chosen
 */
public record Arc(Integer number, UUID sagaContentId,
		Map<String, StoryTranslation> translations) implements ContentBody<Arc> {

	private static final String SAGA_FIELD = "saga";

	@Override
	public Arc normalized() {
		return new Arc(this.number, this.sagaContentId, StoryTranslations.normalized(this.translations));
	}

	@Override
	public Optional<NaturalKey> naturalKey() {
		return Optional.of(new NaturalKey(Saga.NUMBER_FIELD, this.number));
	}

	@Override
	public String nameField() {
		return StoryTranslations.TITLE;
	}

	@Override
	public Set<String> languages() {
		return this.translations.keySet();
	}

	/** The titles: what an arc is called. */
	@Override
	public Map<String, String> names() {
		return StoryTranslations.titles(this.translations);
	}

	/**
	 * The number and the saga, then a title and a description per language - every one
	 * missing.
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
		StoryTranslations.addMissing(this.translations, languages, missing);
		return missing;
	}

}
