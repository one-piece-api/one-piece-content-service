package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.service.exception.DevilFruitNotFoundException;
import dev.onepieceapi.contentservice.service.validation.DevilFruitValidator;
import dev.onepieceapi.exception.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** The Devil Fruit, as the generic workflow sees it. */
@Component
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
class DevilFruitDefinition implements ContentDefinition<DevilFruit, DevilFruitVersionEntity> {

	private final DevilFruitVersionRepository repository;

	private final DevilFruitValidator validator;

	private final DevilFruitRules rules;

	@Override
	public EntityType entityType() {
		return EntityType.DEVIL_FRUIT;
	}

	@Override
	public Version<DevilFruit> toDomain(DevilFruitVersionEntity entity) {
		return DevilFruitVersionMapper.toDomain(entity);
	}

	@Override
	public DevilFruitVersionEntity newVersion(ContentVersionEntity workflow) {
		return new DevilFruitVersionEntity(workflow);
	}

	@Override
	public void rewrite(DevilFruitVersionEntity entity, DevilFruit body, Instant now) {
		DevilFruitVersionMapper.rewrite(entity, body, now);
	}

	@Override
	public NotFoundException notFound(UUID contentId) {
		return new DevilFruitNotFoundException(contentId);
	}

}
