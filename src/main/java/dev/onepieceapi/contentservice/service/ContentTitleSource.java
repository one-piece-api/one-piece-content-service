package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.dashboard.ContentTitle;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * How the contents of one entity type are called where several types are listed together
 * - the dashboard. Each entity knows where its names are stored (implementation plan,
 * D6); the dashboard asks the source of each type for its own contents (Strategy
 * pattern).
 */
public interface ContentTitleSource {

	EntityType entityType();

	/**
	 * The title of each of the given contents, from its most recent version among
	 * {@code statuses}. A content with no version there is not in the map.
	 */
	Map<UUID, ContentTitle> titles(Collection<UUID> contentIds, Collection<VersionStatus> statuses);

}
