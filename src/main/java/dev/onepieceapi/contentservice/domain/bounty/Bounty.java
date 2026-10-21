package dev.onepieceapi.contentservice.domain.bounty;

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
import java.util.UUID;

/**
 * What one version of a bounty says (implementation plan of the Bounty, D1-D5): one
 * wanted poster, named by the poster's name and the amount together, with a description
 * per language and an optional link to the bounty it replaces. Plugged into the generic
 * {@code Content} and {@code Version}, which carry the workflow around it.
 *
 * @param posterName the name printed on the poster; may repeat across bounties
 * @param amount the Berry, 0 or more; beyond 32 bits for the highest ones
 * @param inactive revoked, suspended or its holder dead; whether the bounty stands is
 * {@link BountyValidity}, derived
 * @param previousContentId the content of the bounty this one replaces; null for a first
 * bounty
 * @param holderContentId the content of the character the poster is about; null for none
 * @param imageId the whole poster, optional and not localized; part of the version
 * @param firstAppearance where the poster is revealed; {@link StoryPoint#NONE} for none,
 * which is complete - it is required only to go online
 */
public record Bounty(String posterName, Long amount, BountyCondition condition, boolean inactive,
		UUID previousContentId, UUID holderContentId, Map<String, BountyTranslation> translations, String imageId,
		StoryPoint firstAppearance) implements ContentBody<Bounty>, FirstAppearing {

	public static final int POSTER_NAME_MAX_LENGTH = 100;

	/** What a list is narrowed by, as {@code ContentFilter} names it. */
	public static final String CONDITION_ATTRIBUTE = "condition";

	public static final String VALIDITY_ATTRIBUTE = "validity";

	public static final String HOLDER_ATTRIBUTE = "holder";

	/** What the key is called in a block detail; a violation names its two fields. */
	private static final String KEY_FIELD = "key";

	private static final String POSTER_NAME_FIELD = "posterName";

	private static final String AMOUNT_FIELD = "amount";

	private static final String CONDITION_FIELD = "condition";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String DESCRIPTION_FIELD = "translations[%s].description";

	/** Nothing chosen is {@link StoryPoint#NONE}, not null. */
	public Bounty {
		firstAppearance = StoryPoint.orNone(firstAppearance);
	}

	/** The same bounty with another image, or none. */
	public Bounty withImage(String newImageId) {
		return new Bounty(this.posterName, this.amount, this.condition, this.inactive, this.previousContentId,
				this.holderContentId, this.translations, newImageId, this.firstAppearance);
	}

	@Override
	public Bounty normalized() {
		Map<String, BountyTranslation> written = new TreeMap<>();
		this.translations.forEach((language, translation) -> {
			BountyTranslation normalized = translation.normalized();
			if (!normalized.isEmpty()) {
				written.put(language, normalized);
			}
		});
		return new Bounty(Text.stripToNull(this.posterName), this.amount, this.condition, this.inactive,
				this.previousContentId, this.holderContentId, written, this.imageId, this.firstAppearance);
	}

	@Override
	public Set<String> languages() {
		return this.translations.keySet();
	}

	/** A bounty has no localized name: the natural key alone names it. */
	@Override
	public Map<String, String> names() {
		return Map.of();
	}

	@Override
	public boolean uniqueNames() {
		return false;
	}

	@Override
	public NaturalKey naturalKey() {
		return naturalKeyOf(this.posterName, this.amount);
	}

	/**
	 * The poster's name and the amount together, written as plain digits:
	 * {@code Monkey D. Luffy 3000000000}; no text while either is missing.
	 */
	public static NaturalKey naturalKeyOf(String posterName, Long amount) {
		String text = posterName == null || amount == null ? null : posterName + " " + amount;
		return new NaturalKey(KEY_FIELD, text, List.of(POSTER_NAME_FIELD, AMOUNT_FIELD));
	}

	/**
	 * The poster's name, the amount, the condition and the first appearance, then a
	 * description per language - every one missing. The link, the flag and the image are
	 * never missing.
	 */
	@Override
	public List<String> missingFields(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		if (this.posterName == null) {
			missing.add(POSTER_NAME_FIELD);
		}
		if (this.amount == null) {
			missing.add(AMOUNT_FIELD);
		}
		if (this.condition == null) {
			missing.add(CONDITION_FIELD);
		}
		if (lacksFirstAppearance()) {
			missing.add(FIELD);
		}
		for (String language : languages) {
			if (translationIn(language).description() == null) {
				missing.add(DESCRIPTION_FIELD.formatted(language));
			}
		}
		return missing;
	}

	/**
	 * What it says in this language - nothing at all, when no translation was written.
	 */
	public BountyTranslation translationIn(String language) {
		return this.translations.getOrDefault(language, BountyTranslation.NONE);
	}

}
