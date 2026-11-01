package dev.onepieceapi.contentservice.domain.story;

import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
import dev.onepieceapi.contentservice.domain.workflow.Text;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What one version of a manga volume says (implementation plan of the story structure,
 * S1-S4, S6, S13): its number, a title per language, a cover, and the extras that are
 * never required - the Japanese title and the release per country. A volume has no arc:
 * its arcs are derived from its chapters (S4, S7).
 *
 * @param number 0 or more, unique among the volumes, gaps allowed; null while not written
 * @param japaneseTitle the title as published in Japan, one for every language; optional
 * @param releases the release day per country, ordered by date; optional
 * @param coverImageId its cover (S6), optional and not localized; part of the version, so
 * a different cover is a different version
 * @param translations the title per language code
 */
@Builder(toBuilder = true)
public record Volume(Integer number, String japaneseTitle, List<Release> releases, String coverImageId,
		Map<String, TitleTranslation> translations) implements ContentBody<Volume> {

	/** Nothing written is empty, not null. */
	public Volume {
		releases = releases == null ? List.of() : releases;
		translations = translations == null ? Map.of() : translations;
	}

	/** The same volume with another cover, or none. */
	public Volume withCover(String newCoverImageId) {
		return toBuilder().coverImageId(newCoverImageId).build();
	}

	@Override
	public Volume normalized() {
		return toBuilder().japaneseTitle(Text.stripToNull(this.japaneseTitle))
			.releases(Release.normalized(this.releases))
			.translations(StoryTranslations.normalizedTitles(this.translations))
			.build();
	}

	@Override
	public NaturalKey naturalKey() {
		return new NaturalKey(Saga.NUMBER_FIELD, this.number);
	}

	@Override
	public String nameField() {
		return StoryTranslations.TITLE;
	}

	/** Volumes are told apart by number: two may share a title. */
	@Override
	public boolean uniqueNames() {
		return false;
	}

	@Override
	public Set<String> languages() {
		return this.translations.keySet();
	}

	/** The titles: what a volume is called. */
	@Override
	public Map<String, String> names() {
		return StoryTranslations.titlesOf(this.translations);
	}

	/** The number, then a title per language - every one missing; never an extra. */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.number == null) {
			missing.add(Saga.NUMBER_FIELD);
		}
		StoryTranslations.addMissingTitles(this.translations, languages, missing);
		return missing;
	}

}
