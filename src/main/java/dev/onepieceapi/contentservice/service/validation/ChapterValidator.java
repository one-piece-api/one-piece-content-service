package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.persistence.entity.ChapterVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.StoryVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.story.StoryRelations;
import dev.onepieceapi.exception.web.FieldViolation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules a chapter must meet (implementation plan of the story structure, S2-S5, S13):
 * those of every content - see {@link ContentValidator}, here among the chapters only -
 * so its number is unique, gaps allowed, and its title may repeat; an arc, and a volume
 * when it names one, it may be linked to, as every relation ({@link RelationRules}); a
 * page count of 1 or more; and the days of release checked by {@link ReleaseRules}.
 */
@Component
public class ChapterValidator extends ContentValidator<Chapter, ChapterVersionEntity> {

	private static final String PAGE_COUNT_FIELD = "pageCount";

	private static final String PAGE_COUNT_RULE = "must be 1 or more";

	private final ArcVersionRepository arcRepository;

	private final VolumeVersionRepository volumeRepository;

	@Autowired
	public ChapterValidator(ChapterVersionRepository versionRepository, LanguageRepository languageRepository,
			ArcVersionRepository arcRepository, VolumeVersionRepository volumeRepository) {
		super(versionRepository, languageRepository, StoryVersionMapper::toDomain);
		this.arcRepository = arcRepository;
		this.volumeRepository = volumeRepository;
	}

	@Override
	protected void requireValidRelations(Chapter body) {
		RelationRules.requireLinkable(StoryRelations.CHAPTER_ARC, body, this.arcRepository);
		RelationRules.requireLinkable(StoryRelations.CHAPTER_VOLUME, body, this.volumeRepository);
	}

	@Override
	protected void requireValidFields(Chapter body) {
		List<FieldViolation> violations = new ArrayList<>();
		if (body.pageCount() != null && body.pageCount() < 1) {
			violations.add(new FieldViolation(PAGE_COUNT_FIELD, PAGE_COUNT_RULE));
		}
		violations.addAll(ReleaseRules.violationsOf("releases", body.releases()));
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
	}

}
