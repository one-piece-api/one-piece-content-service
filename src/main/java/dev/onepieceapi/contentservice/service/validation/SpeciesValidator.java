package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.persistence.entity.SpeciesVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.SpeciesVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.species.SpeciesRelations;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.exception.web.FieldViolation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules a Species must meet (docs/user-flows/content-editorial-workflow.md 3.3):
 * those of every content - see {@link ContentValidator} - and a first appearance, when
 * chosen, in a chapter and an episode it may be linked to (3.4.3). Its picture is checked
 * against the {@code species} image profile when uploaded, as every image.
 */
@Component
public class SpeciesValidator extends ContentValidator<Species, SpeciesVersionEntity> {

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	@Autowired
	public SpeciesValidator(SpeciesVersionRepository versionRepository, LanguageRepository languageRepository,
			ChapterVersionRepository chapterRepository, EpisodeVersionRepository episodeRepository) {
		super(versionRepository, languageRepository, SpeciesVersionMapper::toDomain);
		this.chapterRepository = chapterRepository;
		this.episodeRepository = episodeRepository;
	}

	/** Every part of the first appearance at fault is reported. */
	@Override
	protected void requireValidRelations(Species body) {
		List<FieldViolation> violations = new ArrayList<>();
		RelationRules.notLinkable(SpeciesRelations.FIRST_APPEARANCE_CHAPTER, body, "", this.chapterRepository)
			.ifPresent(violations::add);
		RelationRules.notLinkable(SpeciesRelations.FIRST_APPEARANCE_EPISODE, body, "", this.episodeRepository)
			.ifPresent(violations::add);
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
	}

}
