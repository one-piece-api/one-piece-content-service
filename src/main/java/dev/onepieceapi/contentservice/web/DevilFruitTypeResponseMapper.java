package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.domain.ContentVersion;
import dev.onepieceapi.contentservice.domain.EncyclopediaEntry;
import dev.onepieceapi.contentservice.domain.Translation;
import dev.onepieceapi.contentservice.domain.WorkingRevision;
import dev.onepieceapi.contentservice.web.dto.response.ContentVersionDetailResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentVersionResponse;
import dev.onepieceapi.contentservice.web.dto.response.EncyclopediaItemDetailResponse;
import dev.onepieceapi.contentservice.web.dto.response.EncyclopediaItemResponse;
import dev.onepieceapi.contentservice.web.dto.response.ReviewQueueItemResponse;
import dev.onepieceapi.contentservice.web.dto.response.TranslationResponse;
import dev.onepieceapi.contentservice.web.dto.response.WorkingRevisionDetailResponse;
import dev.onepieceapi.contentservice.web.dto.response.WorkingRevisionSummaryResponse;
import lombok.experimental.UtilityClass;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps the domain's
 * {@link WorkingRevision}/{@link ContentVersion}/{@link EncyclopediaEntry} to this
 * service's response shapes - the one place that knows how to pick a list row's display
 * language, so every controller shares the same rule.
 */
@UtilityClass
public class DevilFruitTypeResponseMapper {

	private static final String ENTITY_TYPE = "DEVIL_FRUIT_TYPE";

	/**
	 * Fallback whenever the caller's `Accept-Language` is missing or isn't Italian - see
	 * {@link #preferredLanguageOf(String)}.
	 */
	private static final String DEFAULT_LANGUAGE = "en";

	private static final String ITALIAN = "it";

	public WorkingRevisionDetailResponse toDetail(WorkingRevision revision, boolean everPublished) {
		return new WorkingRevisionDetailResponse(revision.id(), revision.itemId(), revision.romaji(),
				revision.status().name(), toTranslationResponses(revision.translations()), revision.updatedAt(),
				revision.authorEmail(), revision.claimedByEmail(), revision.rejectionReason(), everPublished);
	}

	public WorkingRevisionSummaryResponse toSummary(WorkingRevision revision, String acceptLanguage) {
		return new WorkingRevisionSummaryResponse(revision.id(), revision.itemId(), ENTITY_TYPE, revision.romaji(),
				displayNameOf(revision.translations(), preferredLanguageOf(acceptLanguage)), revision.status().name(),
				revision.updatedAt(), revision.rejectionReason());
	}

	/**
	 * One row of the shared review queue (Step 3) - see {@link ReviewQueueItemResponse}.
	 */
	public ReviewQueueItemResponse toQueueItem(WorkingRevision revision, String acceptLanguage) {
		return new ReviewQueueItemResponse(revision.id(), revision.itemId(), ENTITY_TYPE, revision.romaji(),
				displayNameOf(revision.translations(), preferredLanguageOf(acceptLanguage)), revision.authorEmail(),
				revision.claimedByEmail(), revision.updatedAt());
	}

	/**
	 * English by default, Italian only when the caller's UI is in Italian - the same two
	 * languages the frontend's own language switch offers today (see
	 * {@code AVAILABLE_LOCALES} in one-piece-user-frontend). A future UI locale the
	 * switch doesn't yet cover falls back to this same rule rather than to itself, since
	 * there is no content translation to prefer for it.
	 */
	private String preferredLanguageOf(String acceptLanguage) {
		if (acceptLanguage == null || acceptLanguage.isBlank()) {
			return DEFAULT_LANGUAGE;
		}
		try {
			var ranges = Locale.LanguageRange.parse(acceptLanguage);
			return ITALIAN.equals(Locale.lookupTag(ranges, List.of(ITALIAN, DEFAULT_LANGUAGE))) ? ITALIAN
					: DEFAULT_LANGUAGE;
		}
		catch (IllegalArgumentException ex) {
			return DEFAULT_LANGUAGE;
		}
	}

	/**
	 * Picks a best-effort display name for a compact list row: the preferred language's
	 * name if present and non-blank, otherwise the other one of English/Italian,
	 * otherwise the first remaining translation (by language code) that has one,
	 * otherwise {@code null} - the frontend falls back to "bozza senza nome", matching
	 * the reference mockup's pattern.
	 */
	private String displayNameOf(Map<String, Translation> translations, String preferredLanguage) {
		String secondaryLanguage = ITALIAN.equals(preferredLanguage) ? DEFAULT_LANGUAGE : ITALIAN;
		return translations.entrySet()
			.stream()
			.filter(e -> e.getValue().name() != null && !e.getValue().name().isBlank())
			.sorted(Comparator
				.comparing((Map.Entry<String, Translation> e) -> languageRank(e.getKey(), preferredLanguage,
						secondaryLanguage))
				.thenComparing(Map.Entry::getKey))
			.map(e -> e.getValue().name())
			.findFirst()
			.orElse(null);
	}

	private int languageRank(String languageCode, String preferredLanguage, String secondaryLanguage) {
		if (preferredLanguage.equals(languageCode)) {
			return 0;
		}
		if (secondaryLanguage.equals(languageCode)) {
			return 1;
		}
		return 2;
	}

	/**
	 * "Enciclopedia" (Step 5, extended Step 8): maps any entry kind to its list-row shape
	 * - the one entry point every caller should use, so the `ReviewedCandidate` /
	 * `PublishedItem` / `RetiredItem` switch lives in exactly one place.
	 */
	public EncyclopediaItemResponse toEncyclopediaItem(EncyclopediaEntry entry, String acceptLanguage) {
		String preferredLanguage = preferredLanguageOf(acceptLanguage);
		return switch (entry) {
			case EncyclopediaEntry.ReviewedCandidate rc -> reviewedEncyclopediaItem(rc.revision(), preferredLanguage);
			case EncyclopediaEntry.PublishedItem pi ->
				versionEncyclopediaItem(pi.version(), "PUBLISHED", preferredLanguage);
			case EncyclopediaEntry.RetiredItem ri ->
				versionEncyclopediaItem(ri.lastVersion(), "RETIRED", preferredLanguage);
		};
	}

	/**
	 * Same as {@link #toEncyclopediaItem(EncyclopediaEntry, String)}, for the detail
	 * shape.
	 */
	public EncyclopediaItemDetailResponse toEncyclopediaDetail(EncyclopediaEntry entry) {
		return switch (entry) {
			case EncyclopediaEntry.ReviewedCandidate rc -> reviewedEncyclopediaDetail(rc.revision());
			case EncyclopediaEntry.PublishedItem pi -> versionEncyclopediaDetail(pi.version(), "PUBLISHED");
			case EncyclopediaEntry.RetiredItem ri -> versionEncyclopediaDetail(ri.lastVersion(), "RETIRED");
		};
	}

	private EncyclopediaItemResponse reviewedEncyclopediaItem(WorkingRevision revision, String preferredLanguage) {
		return new EncyclopediaItemResponse(revision.itemId(), revision.id(), ENTITY_TYPE, revision.romaji(),
				displayNameOf(revision.translations(), preferredLanguage), "REVIEWED", revision.updatedAt());
	}

	private EncyclopediaItemResponse versionEncyclopediaItem(ContentVersion version, String status,
			String preferredLanguage) {
		return new EncyclopediaItemResponse(version.itemId(), null, ENTITY_TYPE, version.romaji(),
				displayNameOf(version.translations(), preferredLanguage), status, version.publishedAt());
	}

	private EncyclopediaItemDetailResponse reviewedEncyclopediaDetail(WorkingRevision revision) {
		return new EncyclopediaItemDetailResponse(revision.itemId(), revision.id(), revision.romaji(), "REVIEWED",
				toTranslationResponses(revision.translations()), revision.updatedAt(), null, null);
	}

	private EncyclopediaItemDetailResponse versionEncyclopediaDetail(ContentVersion version, String status) {
		return new EncyclopediaItemDetailResponse(version.itemId(), null, version.romaji(), status,
				toTranslationResponses(version.translations()), version.publishedAt(), version.sequenceNumber(),
				version.publisherEmail());
	}

	/** One row of Step 7's "Storico versioni" - see {@link ContentVersionResponse}. */
	public ContentVersionResponse toVersion(ContentVersion version, boolean live) {
		return new ContentVersionResponse(version.id(), version.sequenceNumber(), version.publisherEmail(),
				version.publishedAt(), live);
	}

	/** One version's full content - see {@link ContentVersionDetailResponse}. */
	public ContentVersionDetailResponse toVersionDetail(ContentVersion version, boolean live) {
		return new ContentVersionDetailResponse(version.id(), version.sequenceNumber(), version.publisherEmail(),
				version.publishedAt(), live, version.romaji(), toTranslationResponses(version.translations()));
	}

	private Map<String, TranslationResponse> toTranslationResponses(Map<String, Translation> translations) {
		return translations.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey,
					e -> new TranslationResponse(e.getValue().name(), e.getValue().description())));
	}

}
