package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.readertime.FirstAppearing;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import dev.onepieceapi.readertime.ReaderPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What every {@link ContentPreviewer} needs, written once: the items a version points to
 * as they are online (reveals plan R6, D5), and a {@link PreviewContext} knowing the
 * numbers of every chapter and episode involved. Numbers are those last approved, as on
 * save: the same as online unless a renumbering is waiting for publication.
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class ReaderPreviews {

	private final StoryNumbering numbering;

	/**
	 * The bodies online of these items of one entity, by content id; none for one
	 * offline.
	 */
	public <T extends ContentBody<T>, E extends VersionBodyEntity> Map<UUID, T> online(
			ContentDefinition<T, E> definition, Collection<UUID> contentIds) {
		if (contentIds.isEmpty()) {
			return Map.of();
		}
		return definition.repository()
			.findMostRecent(Set.copyOf(contentIds), Set.of(VersionStatus.PUBLISHED))
			.stream()
			.collect(Collectors.toMap(VersionBodyEntity::getContentId, entity -> definition.toDomain(entity).body()));
	}

	/** The first appearance of each item online, as a context takes its targets. */
	public static Map<UUID, StoryPoint> firstAppearances(Map<UUID, ? extends FirstAppearing> items) {
		Map<UUID, StoryPoint> points = new HashMap<>();
		items.forEach((id, item) -> points.put(id, item.firstAppearance()));
		return points;
	}

	/**
	 * A context for these points - the version's own and any other the preview compares -
	 * and the targets online with their first appearance.
	 */
	public PreviewContext contextFor(ReaderPosition position, Collection<StoryPoint> points,
			Map<UUID, StoryPoint> onlineTargets) {
		List<StoryPoint> all = Stream.concat(points.stream(), onlineTargets.values().stream()).toList();
		return new PreviewContext(position, this.numbering.numbersOf(all, VersionStatus.approved()), onlineTargets);
	}

}
