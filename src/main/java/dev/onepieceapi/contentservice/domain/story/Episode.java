package dev.onepieceapi.contentservice.domain.story;

import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
import dev.onepieceapi.contentservice.domain.workflow.Text;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What one version of an anime episode says (implementation plan of the story structure,
 * S1-S4, S13): its number, the arc it belongs to if any, a title and description per
 * language, and the extras that are never required - the Japanese title and the broadcast
 * per country.
 *
 * @param number 0 or more, unique among the episodes, gaps allowed; null while not
 * written
 * @param arcContentId the content of its arc; null for none - a filler outside any arc -
 * which is complete
 * @param japaneseTitle the title as published in Japan, one for every language; optional
 * @param broadcasts the broadcast day per country, ordered by date; optional
 * @param translations title and description per language code
 */
@Builder(toBuilder = true)
public record Episode(Integer number, UUID arcContentId, String japaneseTitle, List<Release> broadcasts,
		Map<String, StoryTranslation> translations) implements ContentBody<Episode> {

	/** Nothing written is empty, not null. */
	public Episode {
		broadcasts = broadcasts == null ? List.of() : broadcasts;
		translations = translations == null ? Map.of() : translations;
	}

	@Override
	public Episode normalized() {
		return toBuilder().japaneseTitle(Text.stripToNull(this.japaneseTitle))
			.broadcasts(Release.normalized(this.broadcasts))
			.translations(StoryTranslations.normalized(this.translations))
			.build();
	}

	@Override
	public Optional<NaturalKey> naturalKey() {
		return Optional.of(new NaturalKey(Saga.NUMBER_FIELD, this.number));
	}

	@Override
	public String nameField() {
		return StoryTranslations.TITLE;
	}

	/** Episodes are told apart by number: two may share a title. */
	@Override
	public boolean uniqueNames() {
		return false;
	}

	@Override
	public Set<String> languages() {
		return this.translations.keySet();
	}

	/** The titles: what an episode is called. */
	@Override
	public Map<String, String> names() {
		return StoryTranslations.titles(this.translations);
	}

	/**
	 * The number, then a title and a description per language - every one missing; never
	 * the arc or an extra.
	 */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.number == null) {
			missing.add(Saga.NUMBER_FIELD);
		}
		StoryTranslations.addMissing(this.translations, languages, missing);
		return missing;
	}

}
