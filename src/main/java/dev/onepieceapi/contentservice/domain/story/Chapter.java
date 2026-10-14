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
import java.util.UUID;

/**
 * What one version of a manga chapter says (implementation plan of the story structure,
 * S1-S4, S13): its number, the arc it belongs to, the volume that collects it if any, a
 * title and description per language, and the extras that are never required - the
 * Japanese title, the page count and the release per country.
 *
 * @param number 0 or more (chapter 0 exists), unique among the chapters, gaps allowed;
 * null while not written
 * @param arcContentId the content of its arc, required for review; null while not chosen
 * @param volumeContentId the content of its volume; null for none - recent chapters are
 * not collected yet - which is complete
 * @param japaneseTitle the title as published in Japan, one for every language; optional
 * @param pageCount 1 or more; optional
 * @param releases the release day per country, ordered by date; optional
 * @param translations title and description per language code
 */
@Builder(toBuilder = true)
public record Chapter(Integer number, UUID arcContentId, UUID volumeContentId, String japaneseTitle, Integer pageCount,
		List<Release> releases, Map<String, StoryTranslation> translations) implements ContentBody<Chapter> {

	private static final String ARC_FIELD = "arc";

	/** Nothing written is empty, not null. */
	public Chapter {
		releases = releases == null ? List.of() : releases;
		translations = translations == null ? Map.of() : translations;
	}

	@Override
	public Chapter normalized() {
		return toBuilder().japaneseTitle(Text.stripToNull(this.japaneseTitle))
			.releases(Release.normalized(this.releases))
			.translations(StoryTranslations.normalized(this.translations))
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

	/** Chapters are told apart by number: two may share a title. */
	@Override
	public boolean uniqueNames() {
		return false;
	}

	@Override
	public Set<String> languages() {
		return this.translations.keySet();
	}

	/** The titles: what a chapter is called. */
	@Override
	public Map<String, String> names() {
		return StoryTranslations.titles(this.translations);
	}

	/**
	 * The number and the arc, then a title and a description per language - every one
	 * missing; never the volume or an extra.
	 */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.number == null) {
			missing.add(Saga.NUMBER_FIELD);
		}
		if (this.arcContentId == null) {
			missing.add(ARC_FIELD);
		}
		StoryTranslations.addMissing(this.translations, languages, missing);
		return missing;
	}

}
