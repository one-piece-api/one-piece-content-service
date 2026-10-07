package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A Devil Fruit Type is called by its name in each language, or else by its romaji. */
@Component
class DevilFruitTypeTitleSource extends VersionBodyTitleSource<DevilFruitType, DevilFruitTypeVersionEntity> {

	@Autowired
	DevilFruitTypeTitleSource(DevilFruitTypeVersionRepository repository) {
		super(EntityType.DEVIL_FRUIT_TYPE, repository, DevilFruitTypeVersionMapper::toDomain);
	}

}
