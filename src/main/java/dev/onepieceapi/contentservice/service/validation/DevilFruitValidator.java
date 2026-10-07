package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.exception.web.FieldViolation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The rules a Devil Fruit must meet (implementation plan of the Devil Fruit, D2, D7):
 * those of every content - see {@link ContentValidator}, here among the fruits only - and
 * a type it may be linked to. A type is linkable once one of its versions passed review,
 * and never stops being (an approved version is never deleted), so the check is the same
 * on every save and again at submission. An id that names no type, a content that is not
 * a type and a type with nothing approved are told alike: nobody learns which ids exist.
 */
@Component
public class DevilFruitValidator extends ContentValidator<DevilFruit, DevilFruitVersionEntity> {

	private static final String TYPE_FIELD = "type";

	private static final String NOT_LINKABLE = "must be a Devil Fruit Type with an approved version";

	private final DevilFruitTypeVersionRepository typeRepository;

	@Autowired
	public DevilFruitValidator(DevilFruitVersionRepository versionRepository, LanguageRepository languageRepository,
			DevilFruitTypeVersionRepository typeRepository) {
		super(versionRepository, languageRepository, DevilFruitVersionMapper::toDomain);
		this.typeRepository = typeRepository;
	}

	@Override
	protected void requireValidRelations(DevilFruit body) {
		if (body.typeContentId() != null
				&& !this.typeRepository.existsWithStatus(body.typeContentId(), VersionStatus.approved())) {
			throw new ValueInvalidException(List.of(new FieldViolation(TYPE_FIELD, NOT_LINKABLE)));
		}
	}

}
