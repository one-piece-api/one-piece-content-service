package dev.onepieceapi.contentservice.domain.species;

import dev.onepieceapi.contentservice.domain.readertime.FirstAppearing;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.domain.readertime.Paragraphed;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
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
import java.util.TreeSet;

/**
 * What one version of a Species says (docs/user-flows/content-editorial-workflow.md
 * 3.1.10): the race of a character - Human, Fish-Man, Mink, Giant. One romaji shared by
 * every language, a name per language code, and its description as paragraphs (3.7.3).
 * Plugged into the generic {@code Content} and {@code Version}, which carry the workflow
 * around it.
 *
 * @param imageId its picture (implementation plan of the Species, D5), optional and not
 * localized; part of the version, so a different image is a different version
 * @param firstAppearance the chapter and/or the episode a member is first shown in (D4);
 * {@link StoryPoint#NONE} for none, which a draft may keep - it is required to submit and
 * to go online
 * @param description its paragraphs, in order; none is never null
 */
@Builder(toBuilder = true)
public record Species(String romaji, Map<String, SpeciesTranslation> translations, String imageId,
		StoryPoint firstAppearance,
		List<Paragraph> description) implements ContentBody<Species>, FirstAppearing, Paragraphed {

	public static final int ROMAJI_MAX_LENGTH = 100;

	private static final String ROMAJI_FIELD = "romaji";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String NAME_FIELD = "translations[%s].name";

	/** Nothing chosen is {@link StoryPoint#NONE}, not null; no paragraph is empty. */
	public Species {
		translations = translations == null ? Map.of() : translations;
		firstAppearance = StoryPoint.orNone(firstAppearance);
		description = description == null ? List.of() : List.copyOf(description);
	}

	/** The same species with another image, or none. */
	public Species withImage(String newImageId) {
		return toBuilder().imageId(newImageId).build();
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
		return toBuilder().romaji(Text.stripToNull(this.romaji))
			.translations(written)
			.description(Paragraph.normalized(this.description))
			.build();
	}

	@Override
	public Set<String> languages() {
		Set<String> languages = new TreeSet<>(this.translations.keySet());
		languages.addAll(paragraphLanguages());
		return languages;
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
	 * The romaji and the first appearance, then a name per language, then the paragraphs
	 * and their points - every one missing. The image is never missing.
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
			if (translationIn(language).name() == null) {
				missing.add(NAME_FIELD.formatted(language));
			}
		}
		missing.addAll(missingParagraphs(languages));
		missing.addAll(missingDiscovered());
		return missing;
	}

	@Override
	public Optional<StoryPoint> origin() {
		return Optional.of(this.firstAppearance);
	}

	@Override
	public Map<ParagraphSection, List<Paragraph>> paragraphs() {
		return Map.of(ParagraphSection.DESCRIPTION, this.description);
	}

	/**
	 * What it says in this language - nothing at all, when no translation was written.
	 */
	public SpeciesTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, SpeciesTranslation.NONE);
	}

}
