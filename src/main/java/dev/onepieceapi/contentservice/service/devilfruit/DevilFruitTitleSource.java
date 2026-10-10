package dev.onepieceapi.contentservice.service.devilfruit;

import dev.onepieceapi.contentservice.service.content.VersionBodyTitleSource;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A Devil Fruit is called by its name in each language, or else by its romaji. */
@Component
class DevilFruitTitleSource extends VersionBodyTitleSource<DevilFruit, DevilFruitVersionEntity> {

	@Autowired
	DevilFruitTitleSource(DevilFruitVersionRepository repository) {
		super(EntityType.DEVIL_FRUIT, repository, DevilFruitVersionMapper::toDomain);
	}

}
