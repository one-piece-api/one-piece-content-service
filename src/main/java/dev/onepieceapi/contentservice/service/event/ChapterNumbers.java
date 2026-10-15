package dev.onepieceapi.contentservice.service.event;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ChapterVersionEntity;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The numbers of chapters, by content id, as the rules ordering the story need them
 * (implementation plan of the timeline, T3, T5): as last approved, for what a save says;
 * as online, for what goes online. A chapter with no such version, or no number in it, is
 * not in the map. Each answers in batch, one query.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class ChapterNumbers {

	private final ChapterVersionRepository repository;

	/** As each chapter's most recent version that passed review says. */
	public Map<UUID, Integer> approved(Collection<UUID> chapterContentIds) {
		return numbers(chapterContentIds, VersionStatus.approved());
	}

	/** As each chapter's online version says. */
	public Map<UUID, Integer> online(Collection<UUID> chapterContentIds) {
		return numbers(chapterContentIds, Set.of(VersionStatus.PUBLISHED));
	}

	private Map<UUID, Integer> numbers(Collection<UUID> chapterContentIds, Collection<VersionStatus> statuses) {
		Set<UUID> ids = chapterContentIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
		if (ids.isEmpty()) {
			return Map.of();
		}
		return this.repository.findMostRecent(ids, statuses)
			.stream()
			.filter(chapter -> chapter.getNumber() != null)
			.collect(Collectors.toMap(ChapterVersionEntity::getContentId, ChapterVersionEntity::getNumber));
	}

}
