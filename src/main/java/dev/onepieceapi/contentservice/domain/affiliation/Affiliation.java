package dev.onepieceapi.contentservice.domain.affiliation;

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
 * What one version of an Affiliation says (docs/user-flows/content-editorial-workflow.md
 * 3.1.9): a group a character can belong to - a pirate crew, the Navy, the World
 * Government. One romaji shared by every language, a name and a description per language
 * code. Plugged into the generic {@code Content} and {@code Version}, which carry the
 * workflow around it.
 *
 * @param imageId its emblem (implementation plan of the Affiliation, D5), optional and
 * not localized; part of the version, so a different image is a different version
 * @param firstAppearance the chapter and/or the episode the group is first shown or named
 * in (D4); {@link StoryPoint#NONE} for none, which a draft may keep - it is required to
 * submit and to go online
 * @param kind what sort of group it is (implementation plan of the Character, D10); null
 * while not chosen, which a draft may keep and a version written before kinds existed
 * keeps - it is required to submit
 */
public record Affiliation(String romaji, Map<String, AffiliationTranslation> translations, String imageId,
		StoryPoint firstAppearance, AffiliationKind kind) implements ContentBody<Affiliation>, FirstAppearing {

	public static final int ROMAJI_MAX_LENGTH = 100;

	/**
	 * What the list is narrowed by (D16), as {@code ContentFilter#attributes} names it.
	 */
	public static final String KIND_ATTRIBUTE = "kind";

	private static final String ROMAJI_FIELD = "romaji";

	private static final String KIND_FIELD = "kind";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String NAME_FIELD = "translations[%s].name";

	private static final String DESCRIPTION_FIELD = "translations[%s].description";

	/** Nothing chosen is {@link StoryPoint#NONE}, not null. */
	public Affiliation {
		firstAppearance = StoryPoint.orNone(firstAppearance);
	}

	/** The same affiliation with another image, or none. */
	public Affiliation withImage(String newImageId) {
		return new Affiliation(this.romaji, this.translations, newImageId, this.firstAppearance, this.kind);
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
		return new Affiliation(Text.stripToNull(this.romaji), written, this.imageId, this.firstAppearance, this.kind);
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
	 * The romaji, the kind and the first appearance, then a name and a description per
	 * language - every one missing. The image is never missing.
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
			AffiliationTranslation translation = translationIn(language);
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
	public AffiliationTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, AffiliationTranslation.NONE);
	}

}
