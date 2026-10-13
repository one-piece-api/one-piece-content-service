package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.persistence.entity.ArcVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.SagaVersionRepository;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import dev.onepieceapi.contentservice.service.story.StoryRelations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The rules an arc must meet (implementation plan of the story structure, S2-S5): those
 * of every content - see {@link ContentValidator}, here among the arcs only - and a saga
 * it may be linked to, as every relation ({@link RelationRules}).
 */
@Component
public class ArcValidator extends ContentValidator<Arc, ArcVersionEntity> {

	private final SagaVersionRepository sagaRepository;

	@Autowired
	public ArcValidator(ArcVersionRepository versionRepository, LanguageRepository languageRepository,
			SagaVersionRepository sagaRepository) {
		super(versionRepository, languageRepository, StoryVersionMapper::toDomain);
		this.sagaRepository = sagaRepository;
	}

	@Override
	protected void requireValidRelations(Arc body) {
		RelationRules.requireLinkable(StoryRelations.ARC_SAGA, body, this.sagaRepository);
	}

}
