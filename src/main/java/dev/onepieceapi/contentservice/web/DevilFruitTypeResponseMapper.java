package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.persistence.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.ContentVersionTranslationEntity;
import dev.onepieceapi.contentservice.persistence.TranslationEntity;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionEntity;
import dev.onepieceapi.contentservice.service.EncyclopediaEntry;
import dev.onepieceapi.contentservice.web.dto.ContentVersionResponse;
import dev.onepieceapi.contentservice.web.dto.EncyclopediaItemDetailResponse;
import dev.onepieceapi.contentservice.web.dto.EncyclopediaItemResponse;
import dev.onepieceapi.contentservice.web.dto.ReviewQueueItemResponse;
import dev.onepieceapi.contentservice.web.dto.TranslationResponse;
import dev.onepieceapi.contentservice.web.dto.WorkingRevisionDetailResponse;
import dev.onepieceapi.contentservice.web.dto.WorkingRevisionSummaryResponse;
import lombok.experimental.UtilityClass;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UtilityClass
public class DevilFruitTypeResponseMapper {

	private static final String ENTITY_TYPE = "DEVIL_FRUIT_TYPE";

	/** Preferred language for the "Le mie bozze" summary's display name, see below. */
	private static final String PREFERRED_LANGUAGE = "it";

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
			List<TranslationEntity> translations) {
		return new WorkingRevisionSummaryResponse(revision.getId(), revision.getItemId(), ENTITY_TYPE,
				revision.getRomaji(), displayNameOf(translations), revision.getStatus().name(), revision.getUpdatedAt(),
				revision.getRejectionReason());
	}

	/**
	 * One row of the shared review queue (Step 3) - see {@link ReviewQueueItemResponse}.
	 */
	public ReviewQueueItemResponse toQueueItem(WorkingRevisionEntity revision, List<TranslationEntity> translations) {
		return new ReviewQueueItemResponse(revision.getId(), revision.getItemId(), ENTITY_TYPE, revision.getRomaji(),
				displayNameOf(translations), revision.getAuthorEmail(), revision.getClaimedByEmail(),
				revision.getUpdatedAt());
	}

	/**
	 * Picks a best-effort display name for a compact list row: the preferred language's
	 * name if present and non-blank, otherwise the first other translation (by language
	 * code) that has one, otherwise {@code null} - the frontend falls back to "bozza
	 * senza nome", matching the reference mockup's pattern.
	 */
	private String displayNameOf(List<TranslationEntity> translations) {
		return translations.stream()
			.filter(t -> t.getName() != null && !t.getName().isBlank())
			.sorted(Comparator
				.comparing((TranslationEntity t) -> !PREFERRED_LANGUAGE.equals(t.getId().getLanguageCode()))
				.thenComparing(t -> t.getId().getLanguageCode()))
			.map(TranslationEntity::getName)
			.findFirst()
			.orElse(null);
	}

	/**
	 * "Enciclopedia" (Step 5, extended Step 8): maps any entry kind to its list-row shape
	 * - the one entry point every caller should use, so the `ReviewedCandidate` /
	 * `PublishedItem` / `RetiredItem` switch lives in exactly one place.
	 */
	public EncyclopediaItemResponse toEncyclopediaItem(EncyclopediaEntry entry) {
		return switch (entry) {
			case EncyclopediaEntry.ReviewedCandidate rc -> reviewedEncyclopediaItem(rc.revision(), rc.translations());
			case EncyclopediaEntry.PublishedItem pi ->
				versionEncyclopediaItem(pi.version(), pi.translations(), "PUBLISHED");
			case EncyclopediaEntry.RetiredItem ri ->
				versionEncyclopediaItem(ri.lastVersion(), ri.translations(), "RETIRED");
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
			List<TranslationEntity> translations) {
		return new EncyclopediaItemResponse(revision.getItemId(), revision.getId(), ENTITY_TYPE, revision.getRomaji(),
				displayNameOf(translations), "REVIEWED", revision.getUpdatedAt());
	}

	private EncyclopediaItemResponse versionEncyclopediaItem(ContentVersionEntity version,
			List<ContentVersionTranslationEntity> translations, String status) {
		return new EncyclopediaItemResponse(version.getItemId(), null, ENTITY_TYPE, version.getRomaji(),
				displayNameOfVersion(translations), status, version.getPublishedAt());
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

	private String displayNameOfVersion(List<ContentVersionTranslationEntity> translations) {
		return translations.stream()
			.filter(t -> t.getName() != null && !t.getName().isBlank())
			.sorted(Comparator
				.comparing(
						(ContentVersionTranslationEntity t) -> !PREFERRED_LANGUAGE.equals(t.getId().getLanguageCode()))
				.thenComparing(t -> t.getId().getLanguageCode()))
			.map(ContentVersionTranslationEntity::getName)
			.findFirst()
			.orElse(null);
	}

}
