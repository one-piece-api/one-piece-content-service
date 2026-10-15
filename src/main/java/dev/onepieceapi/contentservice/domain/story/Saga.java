package dev.onepieceapi.contentservice.domain.story;

import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What one version of a saga says (implementation plan of the story structure, S1-S3):
 * its number, the natural key shared by every language, and a title and description per
 * language code. Its span is derived from its arcs' chapters, never stored (S7).
 *
 * @param number 0 or more, unique among the sagas, gaps allowed; null while not written
 */
public record Saga(Integer number, Map<String, StoryTranslation> translations) implements ContentBody<Saga> {

	public static final String NUMBER_FIELD = "number";

	@Override
	public Saga normalized() {
		return new Saga(this.number, StoryTranslations.normalized(this.translations));
	}

	@Override
	public Optional<NaturalKey> naturalKey() {
		return Optional.of(new NaturalKey(NUMBER_FIELD, this.number));
	}

	@Override
	public String nameField() {
		return StoryTranslations.TITLE;
	}

	@Override
	public Set<String> languages() {
		return this.translations.keySet();
	}

	/** The titles: what a saga is called. */
	@Override
	public Map<String, String> names() {
		return StoryTranslations.titles(this.translations);
	}

	/** The number, then a title and a description per language - every one missing. */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.number == null) {
			missing.add(NUMBER_FIELD);
		}
		StoryTranslations.addMissing(this.translations, languages, missing);
		return missing;
	}

}
