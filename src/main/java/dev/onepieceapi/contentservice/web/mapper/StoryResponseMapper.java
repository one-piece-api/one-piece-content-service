package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.web.dto.response.ArcNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.ArcResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.NumberedReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.SagaNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.SagaResponse;
import dev.onepieceapi.contentservice.web.dto.response.StoryTranslationResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * From sagas and arcs to their response bodies: what a version says is mapped here, the
 * workflow around it by {@link ContentResponseMapper}. The saga an arc points to is given
 * already looked up - as today, for a whole page at once - so the mapper reads no
 * database.
 */
@UtilityClass
public class StoryResponseMapper {

	public VersionResponse<SagaResponse> toVersionResponse(VersionAccess<Saga> access) {
		return ContentResponseMapper.toVersionResponse(access, StoryResponseMapper::toResponse);
	}

	public ContentSummaryResponse<SagaNamesResponse> toSummaryResponse(ContentSummary<Saga> summary) {
		return ContentResponseMapper.toSummaryResponse(summary,
				saga -> new SagaNamesResponse(saga.number(), saga.names()));
	}

	public SagaResponse toResponse(Saga saga) {
		return new SagaResponse(saga.number(), toResponses(saga.translations()));
	}

	public VersionResponse<ArcResponse> toVersionResponse(VersionAccess<Arc> access,
			Map<UUID, NumberedReference> sagas) {
		return ContentResponseMapper.toVersionResponse(access, arc -> toResponse(arc, sagas));
	}

	public ContentSummaryResponse<ArcNamesResponse> toSummaryResponse(ContentSummary<Arc> summary,
			Map<UUID, NumberedReference> sagas) {
		return ContentResponseMapper.toSummaryResponse(summary, arc -> new ArcNamesResponse(arc.number(), arc.names(),
				toReferenceResponse(arc.sagaContentId(), sagas)));
	}

	public ArcResponse toResponse(Arc arc, Map<UUID, NumberedReference> sagas) {
		return new ArcResponse(arc.number(), toReferenceResponse(arc.sagaContentId(), sagas),
				toResponses(arc.translations()));
	}

	/**
	 * The content with its number and titles; with only its id when it has nothing
	 * approved to be called by, which the link makes impossible but a read must not fail
	 * for; null for none.
	 */
	public NumberedReferenceResponse toReferenceResponse(UUID contentId, Map<UUID, NumberedReference> references) {
		if (contentId == null) {
			return null;
		}
		NumberedReference reference = references.get(contentId);
		return reference == null ? new NumberedReferenceResponse(contentId, null, Map.of()) : toResponse(reference);
	}

	public NumberedReferenceResponse toResponse(NumberedReference reference) {
		return new NumberedReferenceResponse(reference.id(), reference.number(), reference.names());
	}

	private static Map<String, StoryTranslationResponse> toResponses(Map<String, StoryTranslation> translations) {
		Map<String, StoryTranslationResponse> byLanguage = new TreeMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language,
				new StoryTranslationResponse(translation.title(), translation.description())));
		return byLanguage;
	}

}
