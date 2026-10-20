package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.BountyVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.BountyVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.bounty.BountyChains;
import dev.onepieceapi.contentservice.service.bounty.BountyRelations;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.exception.web.FieldViolation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The rules a bounty must meet (implementation plan of the Bounty, B2): those of every
 * content - see {@link ContentValidator}, here among the bounties only - the links to the
 * bounty it replaces and to its first appearance, which must be linkable like every
 * relation ({@link RelationRules}), and, once they are, the three of the chain, each
 * refused on {@code previous}: not the bounty itself, replaced by at most one (in any
 * version, in any status, drafts included), and no cycle through the latest approved
 * versions. Two drafts may still pass this together; going online checks again
 * ({@code BountyRules}).
 */
@Component
public class BountyValidator extends ContentValidator<Bounty, BountyVersionEntity> {

	private static final String PREVIOUS_FIELD = BountyRelations.PREVIOUS.field();

	private static final String NOT_ANOTHER_BOUNTY = "must be another bounty";

	private static final String ALREADY_REPLACED = "already replaced by another bounty";

	private static final String CYCLE = "would make the chain a cycle";

	private final BountyVersionRepository bountyRepository;

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	private final BountyChains chains;

	@Autowired
	public BountyValidator(BountyVersionRepository versionRepository, LanguageRepository languageRepository,
			ChapterVersionRepository chapterRepository, EpisodeVersionRepository episodeRepository,
			BountyChains chains) {
		super(versionRepository, languageRepository, BountyVersionMapper::toDomain);
		this.bountyRepository = versionRepository;
		this.chapterRepository = chapterRepository;
		this.episodeRepository = episodeRepository;
		this.chains = chains;
	}

	@Override
	protected void requireValidRelations(UUID contentId, Bounty body) {
		requireLinkable(body);
		UUID previous = body.previousContentId();
		if (previous == null) {
			return;
		}
		if (previous.equals(contentId)) {
			throw invalid(NOT_ANOTHER_BOUNTY);
		}
		if (this.bountyRepository.existsOtherWithPrevious(previous, contentId)) {
			throw new ValueAlreadyUsedException(List.of(new FieldViolation(PREVIOUS_FIELD, ALREADY_REPLACED)));
		}
		if (this.chains.reaches(previous, contentId, VersionStatus.approved())) {
			throw invalid(CYCLE);
		}
	}

	/** Every link that is not linkable is reported, not just the first one found. */
	private void requireLinkable(Bounty body) {
		List<FieldViolation> violations = new ArrayList<>();
		RelationRules.notLinkable(BountyRelations.PREVIOUS, body, "", this.bountyRepository).ifPresent(violations::add);
		RelationRules.notLinkable(BountyRelations.FIRST_APPEARANCE_CHAPTER, body, "", this.chapterRepository)
			.ifPresent(violations::add);
		RelationRules.notLinkable(BountyRelations.FIRST_APPEARANCE_EPISODE, body, "", this.episodeRepository)
			.ifPresent(violations::add);
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
	}

	private static ValueInvalidException invalid(String message) {
		return new ValueInvalidException(List.of(new FieldViolation(PREVIOUS_FIELD, message)));
	}

}
