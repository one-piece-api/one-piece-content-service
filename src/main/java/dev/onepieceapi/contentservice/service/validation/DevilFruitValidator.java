package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitRelations;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.exception.web.FieldViolation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The rules a Devil Fruit must meet (implementation plan of the Devil Fruit, D2, D7):
 * those of every content - see {@link ContentValidator}, here among the fruits only - and
 * a type it may be linked to, as every relation ({@link RelationRules}): one of its
 * versions passed review, the same on every save and again at submission. A subcategory,
 * optional, must be one of that type's (implementation plan of the subcategories, S5);
 * none can be named without a type. A first appearance, optional, is a chapter and an
 * episode it may be linked to (implementation plan of the timeline, T7).
 */
@Component
public class DevilFruitValidator extends ContentValidator<DevilFruit, DevilFruitVersionEntity> {

	private static final String SUBCATEGORY_FIELD = "subcategory";

	private static final String NOT_OF_TYPE = "must be a subcategory of its Devil Fruit Type, as last approved";

	private final DevilFruitTypeVersionRepository typeRepository;

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	@Autowired
	public DevilFruitValidator(DevilFruitVersionRepository versionRepository, LanguageRepository languageRepository,
			DevilFruitTypeVersionRepository typeRepository, ChapterVersionRepository chapterRepository,
			EpisodeVersionRepository episodeRepository) {
		super(versionRepository, languageRepository, DevilFruitVersionMapper::toDomain);
		this.typeRepository = typeRepository;
		this.chapterRepository = chapterRepository;
		this.episodeRepository = episodeRepository;
	}

	@Override
	protected void requireValidRelations(DevilFruit body) {
		RelationRules.requireLinkable(DevilFruitRelations.TYPE, body, this.typeRepository);
		if (body.subcategoryId() != null && !subcategoryOfType(body.typeContentId(), body.subcategoryId())) {
			throw new ValueInvalidException(List.of(new FieldViolation(SUBCATEGORY_FIELD, NOT_OF_TYPE)));
		}
		List<FieldViolation> violations = new ArrayList<>();
		RelationRules.notLinkable(DevilFruitRelations.FIRST_APPEARANCE_CHAPTER, body, "", this.chapterRepository)
			.ifPresent(violations::add);
		RelationRules.notLinkable(DevilFruitRelations.FIRST_APPEARANCE_EPISODE, body, "", this.episodeRepository)
			.ifPresent(violations::add);
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
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
