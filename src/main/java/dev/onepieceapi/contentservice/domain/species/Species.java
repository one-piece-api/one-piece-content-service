package dev.onepieceapi.contentservice.domain.species;

import dev.onepieceapi.contentservice.domain.readertime.FirstAppearing;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * What one version of a Species says (docs/user-flows/content-editorial-workflow.md
 * 3.1.10): the race of a character - Human, Fish-Man, Mink, Giant. One romaji shared by
 * every language, a name and a description per language code. Plugged into the generic
 * {@code Content} and {@code Version}, which carry the workflow around it.
 *
 * @param imageId its picture (implementation plan of the Species, D5), optional and not
 * localized; part of the version, so a different image is a different version
 * @param firstAppearance the chapter and/or the episode a member is first shown in (D4);
 * {@link StoryPoint#NONE} for none, which a draft may keep - it is required to submit and
 * to go online
 */
public record Species(String romaji, Map<String, SpeciesTranslation> translations, String imageId,
		StoryPoint firstAppearance) implements ContentBody<Species>, FirstAppearing {

	public static final int ROMAJI_MAX_LENGTH = 100;

	private static final String ROMAJI_FIELD = "romaji";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String NAME_FIELD = "translations[%s].name";

	private static final String DESCRIPTION_FIELD = "translations[%s].description";

	/** Nothing chosen is {@link StoryPoint#NONE}, not null. */
	public Species {
		firstAppearance = StoryPoint.orNone(firstAppearance);
	}

	/** The same species with another image, or none. */
	public Species withImage(String newImageId) {
		return new Species(this.romaji, this.translations, newImageId, this.firstAppearance);
	}

	@Override
	public Species normalized() {
		Map<String, SpeciesTranslation> written = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			SpeciesTranslation normalized = translation.normalized();
			if (!normalized.isEmpty()) {
				written.put(language, normalized);
			}
		});
		return new Species(Text.stripToNull(this.romaji), written, this.imageId, this.firstAppearance);
	}

	@Override
	public Set<String> languages() {
		return this.translations.keySet();
	}

	@Override
	public Map<String, String> names() {
		Map<String, String> names = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			if (translation.name() != null) {
				names.put(language, translation.name());
			}
		});
		return names;
	}

	@Override
	public NaturalKey naturalKey() {
		return new NaturalKey(ROMAJI_FIELD, this.romaji);
	}

	/**
	 * The romaji and the first appearance, then a name and a description per language -
	 * every one missing. The image is never missing.
	 */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.romaji == null) {
			missing.add(ROMAJI_FIELD);
		}
		if (lacksFirstAppearance()) {
			missing.add(FIELD);
		}
		for (String language : languages) {
			SpeciesTranslation translation = translationIn(language);
			if (translation.name() == null) {
				missing.add(NAME_FIELD.formatted(language));
			}
			if (translation.description() == null) {
				missing.add(DESCRIPTION_FIELD.formatted(language));
			}
		}
		return missing;
	}

	/**
	 * What it says in this language - nothing at all, when no translation was written.
	 */
	public SpeciesTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, SpeciesTranslation.NONE);
	}

}
