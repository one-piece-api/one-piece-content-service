package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.persistence.TranslationEntity;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionEntity;
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

	public WorkingRevisionDetailResponse toDetail(WorkingRevisionEntity revision,
			List<TranslationEntity> translations) {
		Map<String, TranslationResponse> byLanguage = translations.stream()
			.collect(Collectors.toMap(t -> t.getId().getLanguageCode(),
					t -> new TranslationResponse(t.getName(), t.getDescription())));
		return new WorkingRevisionDetailResponse(revision.getId(), revision.getItemId(), revision.getRomaji(),
				revision.getStatus().name(), byLanguage, revision.getUpdatedAt(), revision.getAuthorEmail(),
				revision.getClaimedByEmail(), revision.getRejectionReason());
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

}
