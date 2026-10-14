package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.story.Episode;
import dev.onepieceapi.contentservice.persistence.entity.EpisodeVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.story.StoryRelations;
import dev.onepieceapi.exception.web.FieldViolation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The rules an episode must meet (implementation plan of the story structure, S2-S5,
 * S13): those of every content - see {@link ContentValidator}, here among the episodes
 * only - so its number is unique, gaps allowed, and its title may repeat; an arc, when it
 * names one, it may be linked to, as every relation ({@link RelationRules}); and the days
 * of broadcast checked by {@link ReleaseRules}.
 */
@Component
public class EpisodeValidator extends ContentValidator<Episode, EpisodeVersionEntity> {

	private final ArcVersionRepository arcRepository;

	@Autowired
	public EpisodeValidator(EpisodeVersionRepository versionRepository, LanguageRepository languageRepository,
			ArcVersionRepository arcRepository) {
		super(versionRepository, languageRepository, StoryVersionMapper::toDomain);
		this.arcRepository = arcRepository;
	}

	@Override
	protected void requireValidRelations(Episode body) {
		RelationRules.requireLinkable(StoryRelations.EPISODE_ARC, body, this.arcRepository);
	}

	@Override
	protected void requireValidFields(Episode body) {
		List<FieldViolation> violations = ReleaseRules.violationsOf("broadcasts", body.broadcasts());
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
	}

}
