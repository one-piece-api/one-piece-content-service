package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The rules a Devil Fruit Type must meet (docs/user-flows/content-editorial-workflow.md
 * 3.3): those of every content - see {@link ContentValidator} - and nothing of its own.
 */
@Component
public class DevilFruitTypeValidator extends ContentValidator<DevilFruitType, DevilFruitTypeVersionEntity> {

	@Autowired
	public DevilFruitTypeValidator(DevilFruitTypeVersionRepository versionRepository,
			LanguageRepository languageRepository) {
		super(versionRepository, languageRepository, DevilFruitTypeVersionMapper::toDomain);
	}

}
