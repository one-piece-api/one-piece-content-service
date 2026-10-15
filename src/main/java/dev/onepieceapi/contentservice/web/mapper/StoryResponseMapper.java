package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.story.Release;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.story.Volume;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.web.dto.response.ArcNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.ArcResponse;
import dev.onepieceapi.contentservice.web.dto.response.ChapterNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.ChapterResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.EpisodeNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.EpisodeResponse;
import dev.onepieceapi.contentservice.web.dto.response.NumberedReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.ReleaseResponse;
import dev.onepieceapi.contentservice.web.dto.response.SagaNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.SagaResponse;
import dev.onepieceapi.contentservice.web.dto.response.StoryTranslationResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.dto.response.VolumeNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.VolumeResponse;
import dev.onepieceapi.contentservice.web.dto.response.VolumeTranslationResponse;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * From the story entities to their response bodies: what a version says is mapped here,
 * the workflow around it by {@link ContentResponseMapper}. The entities one points to are
 * given already looked up - as today, for a whole page at once - so the mapper reads no
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

	public VersionResponse<VolumeResponse> toVersionResponse(VersionAccess<Volume> access, ImageResponseMapper images) {
		return ContentResponseMapper.toVersionResponse(access, volume -> toResponse(volume, images));
	}

	public ContentSummaryResponse<VolumeNamesResponse> toSummaryResponse(ContentSummary<Volume> summary,
			ImageResponseMapper images) {
		return ContentResponseMapper.toSummaryResponse(summary, volume -> new VolumeNamesResponse(volume.number(),
				volume.names(), images.toResponse(volume.coverImageId())));
	}

	public VolumeResponse toResponse(Volume volume, ImageResponseMapper images) {
		Map<String, VolumeTranslationResponse> translations = new TreeMap<>();
		volume.translations()
			.forEach((language, translation) -> translations.put(language,
					new VolumeTranslationResponse(translation.title())));
		return new VolumeResponse(volume.number(), volume.japaneseTitle(), toReleaseResponses(volume.releases()),
				images.toResponse(volume.coverImageId()), translations);
	}

	public VersionResponse<ChapterResponse> toVersionResponse(VersionAccess<Chapter> access,
			Map<UUID, NumberedReference> arcs, Map<UUID, NumberedReference> volumes) {
		return ContentResponseMapper.toVersionResponse(access, chapter -> toResponse(chapter, arcs, volumes));
	}

	public ContentSummaryResponse<ChapterNamesResponse> toSummaryResponse(ContentSummary<Chapter> summary,
			Map<UUID, NumberedReference> arcs, Map<UUID, NumberedReference> volumes) {
		return ContentResponseMapper.toSummaryResponse(summary,
				chapter -> new ChapterNamesResponse(chapter.number(), chapter.names(),
						toReferenceResponse(chapter.arcContentId(), arcs),
						toReferenceResponse(chapter.volumeContentId(), volumes)));
	}

	public ChapterResponse toResponse(Chapter chapter, Map<UUID, NumberedReference> arcs,
			Map<UUID, NumberedReference> volumes) {
		return new ChapterResponse(chapter.number(), toReferenceResponse(chapter.arcContentId(), arcs),
				toReferenceResponse(chapter.volumeContentId(), volumes), chapter.japaneseTitle(), chapter.pageCount(),
				toReleaseResponses(chapter.releases()), toResponses(chapter.translations()));
	}

	public VersionResponse<EpisodeResponse> toEpisodeVersionResponse(VersionAccess<Episode> access,
			Map<UUID, NumberedReference> arcs) {
		return ContentResponseMapper.toVersionResponse(access, episode -> toResponse(episode, arcs));
	}

	public ContentSummaryResponse<EpisodeNamesResponse> toEpisodeSummaryResponse(ContentSummary<Episode> summary,
			Map<UUID, NumberedReference> arcs) {
		return ContentResponseMapper.toSummaryResponse(summary, episode -> new EpisodeNamesResponse(episode.number(),
				episode.names(), toReferenceResponse(episode.arcContentId(), arcs)));
	}

	public EpisodeResponse toResponse(Episode episode, Map<UUID, NumberedReference> arcs) {
		return new EpisodeResponse(episode.number(), toReferenceResponse(episode.arcContentId(), arcs),
				episode.japaneseTitle(), toReleaseResponses(episode.broadcasts()), toResponses(episode.translations()));
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

	private static List<ReleaseResponse> toReleaseResponses(List<Release> releases) {
		return releases.stream().map(release -> new ReleaseResponse(release.country(), release.date())).toList();
	}

	public Map<String, StoryTranslationResponse> toResponses(Map<String, StoryTranslation> translations) {
		Map<String, StoryTranslationResponse> byLanguage = new TreeMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language,
				new StoryTranslationResponse(translation.title(), translation.description())));
		return byLanguage;
	}

}
