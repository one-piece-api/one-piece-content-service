package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitClassification;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReveal;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.content.RevealRules;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitRelations;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitRevealRules;
import dev.onepieceapi.contentservice.service.event.ChapterNumbers;
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
 * none can be named without a type. A first appearance, optional, is a chapter it may be
 * linked to (implementation plan of the timeline, T7).
 * <p>
 * Its reveals (T9, T13; ADR-0010) follow the {@link RevealRules} and the
 * {@link DevilFruitRevealRules}; the chapter of each and the types of the initial
 * knowledge and of the reveals are relations like the fruit's, and a subcategory one of
 * the type it goes with. Every reveal at fault is reported at once.
 */
@Component
public class DevilFruitValidator extends ContentValidator<DevilFruit, DevilFruitVersionEntity> {

	private static final String SUBCATEGORY_FIELD = "subcategory";

	private static final String NOT_OF_TYPE = "must be a subcategory of its Devil Fruit Type, as last approved";

	private static final String NOT_OF_BELIEF_TYPE = "must be a subcategory"
			+ " of the Devil Fruit Type it goes with, as last approved";

	private final DevilFruitTypeVersionRepository typeRepository;

	private final ChapterVersionRepository chapterRepository;

	private final ChapterNumbers chapterNumbers;

	@Autowired
	public DevilFruitValidator(DevilFruitVersionRepository versionRepository, LanguageRepository languageRepository,
			DevilFruitTypeVersionRepository typeRepository, ChapterVersionRepository chapterRepository,
			ChapterNumbers chapterNumbers) {
		super(versionRepository, languageRepository, DevilFruitVersionMapper::toDomain);
		this.typeRepository = typeRepository;
		this.chapterRepository = chapterRepository;
		this.chapterNumbers = chapterNumbers;
	}

	@Override
	protected void requireValidFields(DevilFruit body) {
		List<FieldViolation> violations = new ArrayList<>(RevealRules.incomplete(body));
		violations.addAll(DevilFruitRevealRules.violations(body));
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
	}

	@Override
	protected void requireValidRelations(DevilFruit body) {
		RelationRules.requireLinkable(DevilFruitRelations.TYPE, body, this.typeRepository);
		if (body.subcategoryId() != null && !subcategoryOfType(body.typeContentId(), body.subcategoryId())) {
			throw new ValueInvalidException(List.of(new FieldViolation(SUBCATEGORY_FIELD, NOT_OF_TYPE)));
		}
		RelationRules.requireLinkable(DevilFruitRelations.FIRST_APPEARANCE, body, this.chapterRepository);
		requireValidReveals(body);
	}

	/**
	 * The initial knowledge and the reveals (T13). Linkable first: an order is only read
	 * between chapters that may be chosen.
	 */
	private void requireValidReveals(DevilFruit body) {
		List<FieldViolation> violations = new ArrayList<>();
		RelationRules.notLinkable(DevilFruitRelations.INITIAL_TYPE, body, "", this.typeRepository)
			.ifPresent(violations::add);
		List<DevilFruitReveal> reveals = body.reveals();
		for (int index = 0; index < reveals.size(); index++) {
			String prefix = RevealRules.REVEAL_PREFIX.formatted(index);
			RelationRules
				.notLinkable(DevilFruitRelations.REVEAL_CHAPTER, reveals.get(index), prefix, this.chapterRepository)
				.ifPresent(violations::add);
			RelationRules.notLinkable(DevilFruitRelations.REVEAL_TYPE, reveals.get(index), prefix, this.typeRepository)
				.ifPresent(violations::add);
		}
		if (violations.isEmpty()) {
			body.classifications()
				.stream()
				.filter(classification -> !classification.isTruth() && classification.subcategoryId() != null)
				.filter(classification -> !subcategoryOfType(classification.typeContentId(),
						classification.subcategoryId()))
				.map(DevilFruitValidator::notOfBeliefType)
				.forEach(violations::add);
			violations.addAll(RevealRules.outOfOrder(body, this.chapterNumbers.approved(body.chapterIds())));
		}
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
	}

	private static FieldViolation notOfBeliefType(DevilFruitClassification classification) {
		return new FieldViolation(classification.prefix() + SUBCATEGORY_FIELD, NOT_OF_BELIEF_TYPE);
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
