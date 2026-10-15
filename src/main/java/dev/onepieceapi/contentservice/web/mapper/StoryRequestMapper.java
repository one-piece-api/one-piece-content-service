package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.domain.story.Release;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.story.Volume;
import dev.onepieceapi.contentservice.domain.story.VolumeTranslation;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.service.story.StoryRelations;
import dev.onepieceapi.contentservice.web.dto.request.ArcListRequest;
import dev.onepieceapi.contentservice.web.dto.request.ArcRequest;
import dev.onepieceapi.contentservice.web.dto.request.ChapterListRequest;
import dev.onepieceapi.contentservice.web.dto.request.ChapterRequest;
import dev.onepieceapi.contentservice.web.dto.request.EpisodeListRequest;
import dev.onepieceapi.contentservice.web.dto.request.EpisodeRequest;
import dev.onepieceapi.contentservice.web.dto.request.ReleaseRequest;
import dev.onepieceapi.contentservice.web.dto.request.SagaRequest;
import dev.onepieceapi.contentservice.web.dto.request.StoryTranslationRequest;
import dev.onepieceapi.contentservice.web.dto.request.VolumeRequest;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * From the requests about the story entities to the domain, as typed: tidying up is left
 * to it.
 */
@UtilityClass
public class StoryRequestMapper {

	public Saga toDomain(SagaRequest request) {
		return new Saga(request.number(), toDomain(request.translations()));
	}

	public Arc toDomain(ArcRequest request) {
		return new Arc(request.number(), request.saga(), toDomain(request.translations()));
	}

	public Volume toDomain(VolumeRequest request) {
		Map<String, VolumeTranslation> translations = new TreeMap<>();
		Optional.ofNullable(request.translations())
			.orElseGet(Map::of)
			.forEach((language, translation) -> translations.put(language,
					new VolumeTranslation(translation == null ? null : translation.title())));
		return new Volume(request.number(), request.japaneseTitle(), toDomain(request.releases()), null, translations);
	}

	public Chapter toDomain(ChapterRequest request) {
		return new Chapter(request.number(), request.arc(), request.volume(), request.japaneseTitle(),
				request.pageCount(), toDomain(request.releases()), toDomain(request.translations()));
	}

	public Episode toDomain(EpisodeRequest request) {
		return new Episode(request.number(), request.arc(), request.japaneseTitle(), toDomain(request.broadcasts()),
				toDomain(request.translations()));
	}

	/**
	 * The common filters, then the arc and the volume when the list is narrowed to them.
	 */
	public ContentFilter toFilter(ChapterListRequest request) {
		var filter = new ContentFilter(request.status(), request.q(), request.author(), request.updatedWithinDays());
		if (request.arc() != null) {
			filter = filter.relatedTo(StoryRelations.CHAPTER_ARC.name(), request.arc());
		}
		return request.volume() == null ? filter
				: filter.relatedTo(StoryRelations.CHAPTER_VOLUME.name(), request.volume());
	}

	/** The common filters, then the arc when the list is narrowed to one. */
	public ContentFilter toFilter(EpisodeListRequest request) {
		var filter = new ContentFilter(request.status(), request.q(), request.author(), request.updatedWithinDays());
		return request.arc() == null ? filter : filter.relatedTo(StoryRelations.EPISODE_ARC.name(), request.arc());
	}

	/** The common filters, then the saga when the list is narrowed to one. */
	public ContentFilter toFilter(ArcListRequest request) {
		var filter = new ContentFilter(request.status(), request.q(), request.author(), request.updatedWithinDays());
		return request.saga() == null ? filter : filter.relatedTo(StoryRelations.ARC_SAGA.name(), request.saga());
	}

	private static List<Release> toDomain(List<ReleaseRequest> requested) {
		return Optional.ofNullable(requested)
			.orElseGet(List::of)
			.stream()
			.map(release -> new Release(release.country(), release.date()))
			.toList();
	}

	/** A language sent with nothing in it is a language with nothing written. */
	public Map<String, StoryTranslation> toDomain(Map<String, StoryTranslationRequest> requested) {
		Map<String, StoryTranslation> translations = new TreeMap<>();
		Optional.ofNullable(requested)
			.orElseGet(Map::of)
			.forEach((language, translation) -> translations.put(language,
					translation == null ? new StoryTranslation(null, null)
							: new StoryTranslation(translation.title(), translation.description())));
		return translations;
	}

}
