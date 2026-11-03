package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.VersionBodyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Looks up the numbers of the chapters and episodes some points name, each as the latest
 * of its versions in the statuses asked: approved on save, online going online.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class StoryNumbering {

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	public StoryNumbers numbersOf(Collection<StoryPoint> points, Collection<VersionStatus> statuses) {
		return new StoryNumbers(
				numbersOf(StoryNumbers.idsOf(points, StoryPoint::chapterContentId), statuses, this.chapterRepository),
				numbersOf(StoryNumbers.idsOf(points, StoryPoint::episodeContentId), statuses, this.episodeRepository));
	}

	private static Map<UUID, Integer> numbersOf(Set<UUID> contentIds, Collection<VersionStatus> statuses,
			VersionBodyRepository<? extends VersionBodyEntity> repository) {
		Map<UUID, Integer> numbers = new HashMap<>();
		if (!contentIds.isEmpty()) {
			repository.findMostRecent(contentIds, statuses).forEach(version -> {
				if (version.naturalKey().value() instanceof Integer number) {
					numbers.put(version.getContentId(), number);
				}
			});
		}
		return numbers;
	}

}
