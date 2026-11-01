package dev.onepieceapi.contentservice.service.validation;

import java.util.List;
import dev.onepieceapi.exception.web.FieldViolation;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.persistence.entity.SagaVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The rules a saga must meet (implementation plan of the story structure, S2, S3): those
 * of every content - see {@link ContentValidator}, here among the sagas only - so its
 * number and each title are unique per language across every version of every other saga;
 * gaps between numbers are allowed.
 */
@Component
public class SagaValidator extends ContentValidator<Saga, SagaVersionEntity> {

	private final ParagraphRules paragraphRules;

	@Autowired
	public SagaValidator(SagaVersionRepository versionRepository, LanguageRepository languageRepository,
			ParagraphRules paragraphRules) {
		super(versionRepository, languageRepository, StoryVersionMapper::toDomain);
		this.paragraphRules = paragraphRules;
	}

	@Override
	protected void requireValidRelations(Saga body) {
		List<FieldViolation> violations = this.paragraphRules.violationsOnSave(body);
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
	}

}
