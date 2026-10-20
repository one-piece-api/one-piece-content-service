package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.AffiliationVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.AffiliationVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.affiliation.AffiliationRelations;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.exception.web.FieldViolation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules an Affiliation must meet (docs/user-flows/content-editorial-workflow.md 3.3):
 * those of every content - see {@link ContentValidator} - and a first appearance, when
 * chosen, in a chapter and an episode it may be linked to (3.4.3). Its emblem is checked
 * against the {@code affiliation} image profile when uploaded, as every image.
 */
@Component
public class AffiliationValidator extends ContentValidator<Affiliation, AffiliationVersionEntity> {

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	@Autowired
	public AffiliationValidator(AffiliationVersionRepository versionRepository, LanguageRepository languageRepository,
			ChapterVersionRepository chapterRepository, EpisodeVersionRepository episodeRepository) {
		super(versionRepository, languageRepository, AffiliationVersionMapper::toDomain);
		this.chapterRepository = chapterRepository;
		this.episodeRepository = episodeRepository;
	}

	/** Every part of the first appearance at fault is reported. */
	@Override
	protected void requireValidRelations(Affiliation body) {
		List<FieldViolation> violations = new ArrayList<>();
		RelationRules.notLinkable(AffiliationRelations.FIRST_APPEARANCE_CHAPTER, body, "", this.chapterRepository)
			.ifPresent(violations::add);
		RelationRules.notLinkable(AffiliationRelations.FIRST_APPEARANCE_EPISODE, body, "", this.episodeRepository)
			.ifPresent(violations::add);
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
	}

}
