package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.service.story.StoryRelations;
import dev.onepieceapi.contentservice.web.dto.request.ArcListRequest;
import dev.onepieceapi.contentservice.web.dto.request.ArcRequest;
import dev.onepieceapi.contentservice.web.dto.request.SagaRequest;
import dev.onepieceapi.contentservice.web.dto.request.StoryTranslationRequest;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * From the requests about sagas and arcs to the domain, as typed: tidying up is left to
 * it.
 */
@UtilityClass
public class StoryRequestMapper {

	public Saga toDomain(SagaRequest request) {
		return new Saga(request.number(), toDomain(request.translations()));
	}

	public Arc toDomain(ArcRequest request) {
		return new Arc(request.number(), request.saga(), toDomain(request.translations()));
	}

	/** The common filters, then the saga when the list is narrowed to one. */
	public ContentFilter toFilter(ArcListRequest request) {
		var filter = new ContentFilter(request.status(), request.q(), request.author(), request.updatedWithinDays());
		return request.saga() == null ? filter : filter.relatedTo(StoryRelations.ARC_SAGA.name(), request.saga());
	}

	/** A language sent with nothing in it is a language with nothing written. */
	private static Map<String, StoryTranslation> toDomain(Map<String, StoryTranslationRequest> requested) {
		Map<String, StoryTranslation> translations = new TreeMap<>();
		Optional.ofNullable(requested)
			.orElseGet(Map::of)
			.forEach((language, translation) -> translations.put(language,
					translation == null ? new StoryTranslation(null, null)
							: new StoryTranslation(translation.title(), translation.description())));
		return translations;
	}

}
