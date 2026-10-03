package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.dashboard.ContentTitle;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** A Devil Fruit Type is called by its name in each language, or else by its romaji. */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class DevilFruitTypeTitleSource implements ContentTitleSource {

	private final DevilFruitTypeVersionRepository repository;

	@Override
	public EntityType entityType() {
		return EntityType.DEVIL_FRUIT_TYPE;
	}

	@Override
	public Map<UUID, ContentTitle> titles(Collection<UUID> contentIds, Collection<VersionStatus> statuses) {
		if (contentIds.isEmpty() || statuses.isEmpty()) {
			return Map.of();
		}
		return this.repository.findMostRecent(contentIds, statuses)
			.stream()
			.collect(Collectors.toMap(DevilFruitTypeVersionEntity::getContentId, DevilFruitTypeTitleSource::titleOf));
	}

	private static ContentTitle titleOf(DevilFruitTypeVersionEntity entity) {
		DevilFruitType body = DevilFruitTypeVersionMapper.toDomain(entity).body();
		return new ContentTitle(body.names(), body.romaji());
	}

}
