package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.dashboard.ContentTitle;
import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import dev.onepieceapi.contentservice.persistence.repository.VersionBodyRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every content is called by its name in each language, or else by its romaji (see
 * {@link ContentBody}) - read from the table of its entity. Each entity has its own,
 * extending this one, e.g. {@code DevilFruitTypeTitleSource}.
 *
 * @param <T> what a version of the entity says
 * @param <E> the entity's version table
 */
@RequiredArgsConstructor
public class VersionBodyTitleSource<T extends ContentBody<T>, E extends VersionBodyEntity>
		implements ContentTitleSource {

	@Getter
	@Accessors(fluent = true)
	private final EntityType entityType;

	private final VersionBodyRepository<E> repository;

	private final Function<E, Version<T>> toDomain;

	@Override
	public Map<UUID, ContentTitle> titles(Collection<UUID> contentIds, Collection<VersionStatus> statuses) {
		if (contentIds.isEmpty() || statuses.isEmpty()) {
			return Map.of();
		}
		return this.repository.findMostRecent(contentIds, statuses)
			.stream()
			.collect(Collectors.toMap(VersionBodyEntity::getContentId, this::titleOf));
	}

	private ContentTitle titleOf(E entity) {
		T body = this.toDomain.apply(entity).body();
		return new ContentTitle(body.names(), body.romaji());
	}

}
