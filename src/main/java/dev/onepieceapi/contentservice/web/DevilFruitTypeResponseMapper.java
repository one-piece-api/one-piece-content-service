package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionTranslationEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEntity;
import dev.onepieceapi.contentservice.persistence.entity.WorkingRevisionEntity;
import dev.onepieceapi.contentservice.service.EncyclopediaEntry;
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

@UtilityClass
public class DevilFruitTypeResponseMapper {

	private static final String ENTITY_TYPE = "DEVIL_FRUIT_TYPE";

	/**
	 * Fallback whenever the caller's `Accept-Language` is missing or isn't Italian - see
	 * {@link #preferredLanguageOf(String)}.
	 */
	private static final String DEFAULT_LANGUAGE = "en";

	private static final String ITALIAN = "it";

	public WorkingRevisionDetailResponse toDetail(WorkingRevisionEntity revision, List<TranslationEntity> translations,
			boolean everPublished) {
		Map<String, TranslationResponse> byLanguage = translations.stream()
			.collect(Collectors.toMap(t -> t.getId().getLanguageCode(),
					t -> new TranslationResponse(t.getName(), t.getDescription())));
		return new WorkingRevisionDetailResponse(revision.getId(), revision.getItemId(), revision.getRomaji(),
				revision.getStatus().name(), byLanguage, revision.getUpdatedAt(), revision.getAuthorEmail(),
				revision.getClaimedByEmail(), revision.getRejectionReason(), everPublished);
	}

	public WorkingRevisionSummaryResponse toSummary(WorkingRevisionEntity revision,
			List<TranslationEntity> translations, String acceptLanguage) {
		return new WorkingRevisionSummaryResponse(revision.getId(), revision.getItemId(), ENTITY_TYPE,
				revision.getRomaji(), displayNameOf(translations, preferredLanguageOf(acceptLanguage)),
				revision.getStatus().name(), revision.getUpdatedAt(), revision.getRejectionReason());
	}

	/**
	 * One row of the shared review queue (Step 3) - see {@link ReviewQueueItemResponse}.
	 */
	public ReviewQueueItemResponse toQueueItem(WorkingRevisionEntity revision, List<TranslationEntity> translations,
			String acceptLanguage) {
		return new ReviewQueueItemResponse(revision.getId(), revision.getItemId(), ENTITY_TYPE, revision.getRomaji(),
				displayNameOf(translations, preferredLanguageOf(acceptLanguage)), revision.getAuthorEmail(),
				revision.getClaimedByEmail(), revision.getUpdatedAt());
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
	private String displayNameOf(List<TranslationEntity> translations, String preferredLanguage) {
		String secondaryLanguage = ITALIAN.equals(preferredLanguage) ? DEFAULT_LANGUAGE : ITALIAN;
		return translations.stream()
			.filter(t -> t.getName() != null && !t.getName().isBlank())
			.sorted(Comparator
				.comparing((TranslationEntity t) -> languageRank(t.getId().getLanguageCode(), preferredLanguage,
						secondaryLanguage))
				.thenComparing(t -> t.getId().getLanguageCode()))
			.map(TranslationEntity::getName)
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
			case EncyclopediaEntry.ReviewedCandidate rc ->
				reviewedEncyclopediaItem(rc.revision(), rc.translations(), preferredLanguage);
			case EncyclopediaEntry.PublishedItem pi ->
				versionEncyclopediaItem(pi.version(), pi.translations(), "PUBLISHED", preferredLanguage);
			case EncyclopediaEntry.RetiredItem ri ->
				versionEncyclopediaItem(ri.lastVersion(), ri.translations(), "RETIRED", preferredLanguage);
		};
	}

	/** Same as {@link #toEncyclopediaItem(EncyclopediaEntry)}, for the detail shape. */
	public EncyclopediaItemDetailResponse toEncyclopediaDetail(EncyclopediaEntry entry) {
		return switch (entry) {
			case EncyclopediaEntry.ReviewedCandidate rc -> reviewedEncyclopediaDetail(rc.revision(), rc.translations());
			case EncyclopediaEntry.PublishedItem pi ->
				versionEncyclopediaDetail(pi.version(), pi.translations(), "PUBLISHED");
			case EncyclopediaEntry.RetiredItem ri ->
				versionEncyclopediaDetail(ri.lastVersion(), ri.translations(), "RETIRED");
		};
	}

	private EncyclopediaItemResponse reviewedEncyclopediaItem(WorkingRevisionEntity revision,
			List<TranslationEntity> translations, String preferredLanguage) {
		return new EncyclopediaItemResponse(revision.getItemId(), revision.getId(), ENTITY_TYPE, revision.getRomaji(),
				displayNameOf(translations, preferredLanguage), "REVIEWED", revision.getUpdatedAt());
	}

	private EncyclopediaItemResponse versionEncyclopediaItem(ContentVersionEntity version,
			List<ContentVersionTranslationEntity> translations, String status, String preferredLanguage) {
		return new EncyclopediaItemResponse(version.getItemId(), null, ENTITY_TYPE, version.getRomaji(),
				displayNameOfVersion(translations, preferredLanguage), status, version.getPublishedAt());
	}

	private EncyclopediaItemDetailResponse reviewedEncyclopediaDetail(WorkingRevisionEntity revision,
			List<TranslationEntity> translations) {
		Map<String, TranslationResponse> byLanguage = translations.stream()
			.collect(Collectors.toMap(t -> t.getId().getLanguageCode(),
					t -> new TranslationResponse(t.getName(), t.getDescription())));
		return new EncyclopediaItemDetailResponse(revision.getItemId(), revision.getId(), revision.getRomaji(),
				"REVIEWED", byLanguage, revision.getUpdatedAt(), null, null);
	}

	private EncyclopediaItemDetailResponse versionEncyclopediaDetail(ContentVersionEntity version,
			List<ContentVersionTranslationEntity> translations, String status) {
		Map<String, TranslationResponse> byLanguage = translations.stream()
			.collect(Collectors.toMap(t -> t.getId().getLanguageCode(),
					t -> new TranslationResponse(t.getName(), t.getDescription())));
		return new EncyclopediaItemDetailResponse(version.getItemId(), null, version.getRomaji(), status, byLanguage,
				version.getPublishedAt(), version.getSequenceNumber(), version.getPublisherEmail());
	}

	/** One row of Step 7's "Storico versioni" - see {@link ContentVersionResponse}. */
	public ContentVersionResponse toVersion(ContentVersionEntity version, boolean live) {
		return new ContentVersionResponse(version.getId(), version.getSequenceNumber(), version.getPublisherEmail(),
				version.getPublishedAt(), live);
	}

	/** One version's full content - see {@link ContentVersionDetailResponse}. */
	public ContentVersionDetailResponse toVersionDetail(ContentVersionEntity version, boolean live,
			List<ContentVersionTranslationEntity> translations) {
		Map<String, TranslationResponse> byLanguage = translations.stream()
			.collect(Collectors.toMap(t -> t.getId().getLanguageCode(),
					t -> new TranslationResponse(t.getName(), t.getDescription())));
		return new ContentVersionDetailResponse(version.getId(), version.getSequenceNumber(),
				version.getPublisherEmail(), version.getPublishedAt(), live, version.getRomaji(), byLanguage);
	}

	private String displayNameOfVersion(List<ContentVersionTranslationEntity> translations, String preferredLanguage) {
		String secondaryLanguage = ITALIAN.equals(preferredLanguage) ? DEFAULT_LANGUAGE : ITALIAN;
		return translations.stream()
			.filter(t -> t.getName() != null && !t.getName().isBlank())
			.sorted(Comparator
				.comparing((ContentVersionTranslationEntity t) -> languageRank(t.getId().getLanguageCode(),
						preferredLanguage, secondaryLanguage))
				.thenComparing(t -> t.getId().getLanguageCode()))
			.map(ContentVersionTranslationEntity::getName)
			.findFirst()
			.orElse(null);
	}

}
