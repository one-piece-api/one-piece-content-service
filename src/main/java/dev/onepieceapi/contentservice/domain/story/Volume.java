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
import java.util.TreeMap;

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
		Map<String, VolumeTranslation> translations) implements ContentBody<Volume> {

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
		Map<String, VolumeTranslation> written = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			VolumeTranslation normalized = translation.normalized();
			if (!normalized.isEmpty()) {
				written.put(language, normalized);
			}
		});
		return toBuilder().japaneseTitle(Text.stripToNull(this.japaneseTitle))
			.releases(Release.normalized(this.releases))
			.translations(written)
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
		Map<String, String> titles = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			if (translation.title() != null) {
				titles.put(language, translation.title());
			}
		});
		return titles;
	}

	/** The number, then a title per language - every one missing; never an extra. */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.number == null) {
			missing.add(Saga.NUMBER_FIELD);
		}
		for (String language : languages) {
			VolumeTranslation translation = this.translations.get(language);
			if (translation == null || translation.title() == null) {
				missing.add(StoryTranslations.TITLE_FIELD.formatted(language));
			}
		}
		return missing;
	}

}
