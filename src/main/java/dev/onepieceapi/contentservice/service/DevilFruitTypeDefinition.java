package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.service.exception.DevilFruitTypeNotFoundException;
import dev.onepieceapi.contentservice.service.validation.DevilFruitTypeValidator;
import dev.onepieceapi.exception.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.UUID;

/** The Devil Fruit Type, as the generic workflow sees it. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
class DevilFruitTypeDefinition implements ContentDefinition<DevilFruitType, DevilFruitTypeVersionEntity> {

	private final DevilFruitTypeVersionRepository repository;

	private final DevilFruitTypeValidator validator;

	private final DevilFruitTypeRules rules;

	@Override
	public EntityType entityType() {
		return EntityType.DEVIL_FRUIT_TYPE;
	}

	@Override
	public Version<DevilFruitType> toDomain(DevilFruitTypeVersionEntity entity) {
		return DevilFruitTypeVersionMapper.toDomain(entity);
	}

	@Override
	public DevilFruitTypeVersionEntity newVersion(ContentVersionEntity workflow) {
		return new DevilFruitTypeVersionEntity(workflow);
	}

	@Override
	public void rewrite(DevilFruitTypeVersionEntity entity, DevilFruitType body, Instant now) {
		DevilFruitTypeVersionMapper.rewrite(entity, body, now);
	}

	@Override
	public NotFoundException notFound(UUID contentId) {
		return new DevilFruitTypeNotFoundException(contentId);
	}

}
