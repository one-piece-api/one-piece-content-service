package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.readertime.FirstAppearing;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * The chapters and the episodes first appearances and other points of the reader axis
 * point to (ADR-0008), as they are today, in one map by content id: a chapter and an
 * episode never share one.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class FirstAppearanceLinks {

	private final ChapterLinks chapters;

	private final EpisodeLinks episodes;

	/** The chapters and the episodes the given items first appear in. */
	public <S extends FirstAppearing> Map<UUID, NumberedReference> referencesFor(Collection<S> items) {
		Map<UUID, NumberedReference> references = new HashMap<>(
				this.chapters.referencesFor(items, FirstAppearing::firstAppearanceChapterId));
		references.putAll(this.episodes.referencesFor(items, FirstAppearing::firstAppearanceEpisodeId));
		return references;
	}

	/** The chapters and the episodes of these points. */
	public Map<UUID, NumberedReference> referencesOfPoints(Collection<StoryPoint> points) {
		return referencesOf(idsOf(points, StoryPoint::chapterContentId), idsOf(points, StoryPoint::episodeContentId));
	}

	private static List<UUID> idsOf(Collection<StoryPoint> points, Function<StoryPoint, UUID> part) {
		return points.stream().map(part).filter(Objects::nonNull).distinct().toList();
	}

	/** The chapters and the episodes with these content ids. */
	public Map<UUID, NumberedReference> referencesOf(Collection<UUID> chapterIds, Collection<UUID> episodeIds) {
		Map<UUID, NumberedReference> references = new HashMap<>(this.chapters.referencesOf(chapterIds));
		references.putAll(this.episodes.referencesOf(episodeIds));
		return references;
	}

}
