package dev.onepieceapi.contentservice.domain.affiliation;

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
 * What one version of an Affiliation says (docs/user-flows/content-editorial-workflow.md
 * 3.1.9): a group a character can belong to - a pirate crew, the Navy, the World
 * Government. One romaji shared by every language, a name per language code, and its
 * description as paragraphs (3.7.3). Plugged into the generic {@code Content} and
 * {@code Version}, which carry the workflow around it.
 *
 * @param imageId its emblem (implementation plan of the Affiliation, D5), optional and
 * not localized; part of the version, so a different image is a different version
 * @param firstAppearance the chapter and/or the episode the group is first shown or named
 * in (D4); {@link StoryPoint#NONE} for none, which a draft may keep - it is required to
 * submit and to go online
 * @param kind what sort of group it is (implementation plan of the Character, D10); null
 * while not chosen, which a draft may keep and a version written before kinds existed
 * keeps - it is required to submit
 * @param description its paragraphs, in order; none is never null
 */
@Builder(toBuilder = true)
public record Affiliation(String romaji, Map<String, AffiliationTranslation> translations, String imageId,
		StoryPoint firstAppearance, AffiliationKind kind,
		List<Paragraph> description) implements ContentBody<Affiliation>, FirstAppearing, Paragraphed {

	public static final int ROMAJI_MAX_LENGTH = 100;

	/**
	 * What the list is narrowed by (D16), as {@code ContentFilter#attributes} names it.
	 */
	public static final String KIND_ATTRIBUTE = "kind";

	private static final String ROMAJI_FIELD = "romaji";

	private static final String KIND_FIELD = "kind";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String NAME_FIELD = "translations[%s].name";

	/** Nothing chosen is {@link StoryPoint#NONE}, not null; no list is null. */
	public Affiliation {
		translations = translations == null ? Map.of() : translations;
		firstAppearance = StoryPoint.orNone(firstAppearance);
		description = description == null ? List.of() : List.copyOf(description);
	}

	/** The same affiliation with another image, or none. */
	public Affiliation withImage(String newImageId) {
		return toBuilder().imageId(newImageId).build();
	}

	@Override
	public Affiliation normalized() {
		Map<String, AffiliationTranslation> written = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			AffiliationTranslation normalized = translation.normalized();
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
	 * The romaji, the kind and the first appearance, then a name per language, then the
	 * paragraphs and their points - every one missing. The image is never missing.
	 */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.romaji == null) {
			missing.add(ROMAJI_FIELD);
		}
		if (this.kind == null) {
			missing.add(KIND_FIELD);
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
	public AffiliationTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, AffiliationTranslation.NONE);
	}

}
