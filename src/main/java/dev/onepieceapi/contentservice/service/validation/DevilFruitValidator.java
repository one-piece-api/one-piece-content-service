package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitRelations;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.exception.web.FieldViolation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * The rules a Devil Fruit must meet (implementation plan of the Devil Fruit, D2, D7):
 * those of every content - see {@link ContentValidator}, here among the fruits only - and
 * a type it may be linked to, as every relation ({@link RelationRules}): one of its
 * versions passed review, the same on every save and again at submission. A subcategory,
 * optional, must be one of that type's (implementation plan of the subcategories, S5);
 * none can be named without a type.
 */
@Component
public class DevilFruitValidator extends ContentValidator<DevilFruit, DevilFruitVersionEntity> {

	private static final String SUBCATEGORY_FIELD = "subcategory";

	private static final String NOT_OF_TYPE = "must be a subcategory of its Devil Fruit Type, as last approved";

	private final DevilFruitTypeVersionRepository typeRepository;

	@Autowired
	public DevilFruitValidator(DevilFruitVersionRepository versionRepository, LanguageRepository languageRepository,
			DevilFruitTypeVersionRepository typeRepository) {
		super(versionRepository, languageRepository, DevilFruitVersionMapper::toDomain);
		this.typeRepository = typeRepository;
	}

	@Override
	protected void requireValidRelations(DevilFruit body) {
		RelationRules.requireLinkable(DevilFruitRelations.TYPE, body, this.typeRepository);
		if (body.subcategoryId() != null && !subcategoryOfType(body.typeContentId(), body.subcategoryId())) {
			throw new ValueInvalidException(List.of(new FieldViolation(SUBCATEGORY_FIELD, NOT_OF_TYPE)));
		}
	}

	/**
	 * Whether the type, as it was last approved, has this subcategory (implementation
	 * plan of the subcategories, S5): one an approved version removed is no longer
	 * offered.
	 */
	private boolean subcategoryOfType(UUID typeContentId, UUID subcategoryId) {
		return typeContentId != null
				&& this.typeRepository.findMostRecent(List.of(typeContentId), VersionStatus.approved())
					.stream()
					.map(DevilFruitTypeVersionMapper::toDomain)
					.anyMatch(type -> type.body().subcategory(subcategoryId).isPresent());
	}

}
