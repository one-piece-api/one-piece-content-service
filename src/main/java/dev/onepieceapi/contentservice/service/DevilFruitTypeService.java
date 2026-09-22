package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.persistence.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.ContentVersionTranslationEntity;
import dev.onepieceapi.contentservice.persistence.ContentVersionTranslationRepository;
import dev.onepieceapi.contentservice.persistence.DevilFruitTypeItemEntity;
import dev.onepieceapi.contentservice.persistence.DevilFruitTypeItemRepository;
import dev.onepieceapi.contentservice.persistence.TranslationEntity;
import dev.onepieceapi.contentservice.persistence.TranslationId;
import dev.onepieceapi.contentservice.persistence.TranslationRepository;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionEntity;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionRepository;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionStatus;
import dev.onepieceapi.contentservice.service.exception.EncyclopediaItemNotFoundException;
import dev.onepieceapi.contentservice.service.exception.InvalidStatusTransitionException;
import dev.onepieceapi.contentservice.service.exception.NotClaimantException;
import dev.onepieceapi.contentservice.service.exception.ReviewAlreadyClaimedException;
import dev.onepieceapi.contentservice.service.exception.ReviewSlotOccupiedException;
import dev.onepieceapi.contentservice.service.exception.WorkingRevisionNotFoundException;
import dev.onepieceapi.contentservice.web.dto.TranslationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UF-CNT-01 through UF-CNT-07/UF-CNT-13/UF-CNT-14: create/edit a private working
 * revision, submit it for review or withdraw it, claim/release/approve/reject one in the
 * shared review queue, and publish an approved candidate to the encyclopedia. Ownership
 * (4.1/7.5 - a draft is visible only to its own author) and claim (4.1/7.6 - only the
 * current claimant may approve/reject) are both enforced here, not by
 * {@code SecuredEndpoint}, which only knows about the coarse
 * {@code content:write}/{@code content:review}/{@code content:publish} permissions, not
 * who owns or claims which row. Content-shape checks (is a draft complete enough to
 * submit, are its languages known, is a rejection reason present) are delegated to
 * {@link ContentValidator} rather than inlined here - this class stays about
 * orchestrating state transitions, not about what makes a field valid.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitTypeService {

	private static final String AUDIT_ACTION_CREATE = "DEVIL_FRUIT_TYPE_DRAFT_CREATED";

	private static final String AUDIT_ACTION_EDIT = "DEVIL_FRUIT_TYPE_DRAFT_EDITED";

	private static final String AUDIT_ACTION_SUBMIT = "DEVIL_FRUIT_TYPE_SUBMITTED_FOR_REVIEW";

	private static final String AUDIT_ACTION_WITHDRAW = "DEVIL_FRUIT_TYPE_WITHDRAWN_TO_DRAFT";

	private static final String AUDIT_ACTION_CLAIM = "DEVIL_FRUIT_TYPE_CLAIMED";

	private static final String AUDIT_ACTION_RELEASE = "DEVIL_FRUIT_TYPE_RELEASED";

	private static final String AUDIT_ACTION_APPROVE = "DEVIL_FRUIT_TYPE_APPROVED";

	private static final String AUDIT_ACTION_REJECT = "DEVIL_FRUIT_TYPE_REJECTED";

	private static final String AUDIT_ACTION_SUPERSEDE = "DEVIL_FRUIT_TYPE_SUPERSEDED";

	private static final String AUDIT_ACTION_PUBLISH = "DEVIL_FRUIT_TYPE_PUBLISHED";

	private final DevilFruitTypeItemRepository itemRepository;

	private final WorkingRevisionRepository workingRevisionRepository;

	private final TranslationRepository translationRepository;

	private final ContentVersionRepository contentVersionRepository;

	private final ContentVersionTranslationRepository contentVersionTranslationRepository;

	private final ContentValidator contentValidator;

	private final AuditLogService auditLogService;

	private final Clock clock;

	@Transactional
	public WorkingRevisionEntity createDraft(UUID authorId, String authorEmail) {
		var now = this.clock.instant();
		var item = this.itemRepository.save(new DevilFruitTypeItemEntity(UUID.randomUUID(), now));
		var revision = this.workingRevisionRepository.save(new WorkingRevisionEntity(UUID.randomUUID(), item.getId(),
				authorId, authorEmail, WorkingRevisionStatus.DRAFT, now));
		this.auditLogService.record(AUDIT_ACTION_CREATE, authorId, authorEmail, item.getId(), null, null);
		return revision;
	}

	@Transactional
	public WorkingRevisionEntity updateDraft(UUID workingRevisionId, UUID authorId, String authorEmail, String romaji,
			Map<String, TranslationRequest> translations) {
		var revision = ownWorkingRevisionOrThrow(workingRevisionId, authorId);
		requireStatus(revision, WorkingRevisionStatus.DRAFT, "edit");

		var requestedLanguages = translations == null ? Set.<String>of() : translations.keySet();
		this.contentValidator.requireKnownLanguages(requestedLanguages);

		revision.setRomaji(romaji);
		revision.setUpdatedAt(this.clock.instant());

		if (translations != null) {
			for (var entry : translations.entrySet()) {
				upsertTranslation(workingRevisionId, entry.getKey(), entry.getValue());
			}
		}

		this.auditLogService.record(AUDIT_ACTION_EDIT, authorId, authorEmail, revision.getItemId(), romaji, null);
		return revision;
	}

	@Transactional
	public WorkingRevisionEntity submitForReview(UUID workingRevisionId, UUID authorId, String authorEmail) {
		var revision = ownWorkingRevisionOrThrow(workingRevisionId, authorId);
		requireStatus(revision, WorkingRevisionStatus.DRAFT, "submit for review");
		this.contentValidator.requireCompleteForSubmission(revision);
		if (this.workingRevisionRepository.existsByItemIdAndStatusAndIdNot(revision.getItemId(),
				WorkingRevisionStatus.IN_REVIEW, revision.getId())) {
			throw new ReviewSlotOccupiedException(revision.getItemId());
		}

		revision.setStatus(WorkingRevisionStatus.IN_REVIEW);
		revision.setUpdatedAt(this.clock.instant());
		this.auditLogService.record(AUDIT_ACTION_SUBMIT, authorId, authorEmail, revision.getItemId(),
				revision.getRomaji(), null);
		return revision;
	}

	@Transactional
	public WorkingRevisionEntity withdrawToDraft(UUID workingRevisionId, UUID authorId, String authorEmail) {
		var revision = ownWorkingRevisionOrThrow(workingRevisionId, authorId);
		requireStatus(revision, WorkingRevisionStatus.IN_REVIEW, "withdraw to draft");
		if (revision.getClaimedBy() != null) {
			throw new ReviewAlreadyClaimedException(workingRevisionId);
		}

		revision.setStatus(WorkingRevisionStatus.DRAFT);
		revision.setUpdatedAt(this.clock.instant());
		this.auditLogService.record(AUDIT_ACTION_WITHDRAW, authorId, authorEmail, revision.getItemId(),
				revision.getRomaji(), null);
		return revision;
	}

	/**
	 * UF-CNT-13: claims an unclaimed, queued working revision for the acting REVIEWER.
	 */
	@Transactional
	public WorkingRevisionEntity claim(UUID workingRevisionId, UUID reviewerId, String reviewerEmail) {
		var revision = workingRevisionOrThrow(workingRevisionId);
		requireStatus(revision, WorkingRevisionStatus.IN_REVIEW, "claim");
		if (revision.getClaimedBy() != null) {
			throw new ReviewAlreadyClaimedException(workingRevisionId);
		}

		revision.setClaimedBy(reviewerId);
		revision.setClaimedByEmail(reviewerEmail);
		revision.setUpdatedAt(this.clock.instant());
		this.auditLogService.record(AUDIT_ACTION_CLAIM, reviewerId, reviewerEmail, revision.getItemId(),
				revision.getRomaji(), null);
		return revision;
	}

	/** UF-CNT-14: releases the acting REVIEWER's own claim, making it claimable again. */
	@Transactional
	public WorkingRevisionEntity release(UUID workingRevisionId, UUID reviewerId, String reviewerEmail) {
		var revision = workingRevisionOrThrow(workingRevisionId);
		requireStatus(revision, WorkingRevisionStatus.IN_REVIEW, "release");
		requireClaimant(revision, reviewerId, "release");

		clearClaim(revision);
		revision.setUpdatedAt(this.clock.instant());
		this.auditLogService.record(AUDIT_ACTION_RELEASE, reviewerId, reviewerEmail, revision.getItemId(),
				revision.getRomaji(), null);
		return revision;
	}

	/**
	 * UF-CNT-05: approves the acting REVIEWER's own claim -
	 * {@code IN_REVIEW -> REVIEWED}. If the item already has a different {@code REVIEWED}
	 * working revision, it is immediately superseded (4.1): this one becomes the sole
	 * active candidate.
	 */
	@Transactional
	public WorkingRevisionEntity approve(UUID workingRevisionId, UUID reviewerId, String reviewerEmail) {
		var revision = workingRevisionOrThrow(workingRevisionId);
		requireStatus(revision, WorkingRevisionStatus.IN_REVIEW, "approve");
		requireClaimant(revision, reviewerId, "approve");

		supersedeReviewedSibling(revision, reviewerId, reviewerEmail);

		revision.setStatus(WorkingRevisionStatus.REVIEWED);
		clearClaim(revision);
		revision.setUpdatedAt(this.clock.instant());
		this.auditLogService.record(AUDIT_ACTION_APPROVE, reviewerId, reviewerEmail, revision.getItemId(),
				revision.getRomaji(), null);
		return revision;
	}

	/**
	 * UF-CNT-06: rejects the acting REVIEWER's own claim with a mandatory reason -
	 * {@code IN_REVIEW -> DRAFT}, editable again by its author, the reason attached so
	 * they can see what to correct.
	 */
	@Transactional
	public WorkingRevisionEntity reject(UUID workingRevisionId, UUID reviewerId, String reviewerEmail, String reason) {
		var revision = workingRevisionOrThrow(workingRevisionId);
		requireStatus(revision, WorkingRevisionStatus.IN_REVIEW, "reject");
		requireClaimant(revision, reviewerId, "reject");
		this.contentValidator.requireReason(reason);

		revision.setStatus(WorkingRevisionStatus.DRAFT);
		revision.setRejectionReason(reason);
		clearClaim(revision);
		revision.setUpdatedAt(this.clock.instant());
		this.auditLogService.record(AUDIT_ACTION_REJECT, reviewerId, reviewerEmail, revision.getItemId(),
				revision.getRomaji(), reason);
		return revision;
	}

	/**
	 * UF-CNT-07: publishes the item's active reviewed candidate - creates an immutable
	 * version snapshot, repoints the item's live pointer to it, and consumes the
	 * candidate ({@code REVIEWED -> PUBLISHED}, permanently terminal).
	 */
	@Transactional
	public WorkingRevisionEntity publish(UUID workingRevisionId, UUID publisherId, String publisherEmail) {
		var revision = workingRevisionOrThrow(workingRevisionId);
		requireStatus(revision, WorkingRevisionStatus.REVIEWED, "publish");

		var now = this.clock.instant();
		var sequenceNumber = (int) this.contentVersionRepository.countByItemId(revision.getItemId()) + 1;
		var version = this.contentVersionRepository.save(new ContentVersionEntity(UUID.randomUUID(),
				revision.getItemId(), sequenceNumber, revision.getRomaji(), publisherId, publisherEmail, now));
		for (var translation : translationsOf(revision.getId())) {
			this.contentVersionTranslationRepository.save(new ContentVersionTranslationEntity(version.getId(),
					translation.getId().getLanguageCode(), translation.getName(), translation.getDescription()));
		}

		var item = this.itemRepository.findById(revision.getItemId()).orElseThrow();
		item.setLiveVersionId(version.getId());

		revision.setStatus(WorkingRevisionStatus.PUBLISHED);
		revision.setUpdatedAt(now);
		this.auditLogService.record(AUDIT_ACTION_PUBLISH, publisherId, publisherEmail, revision.getItemId(),
				revision.getRomaji(), "v" + sequenceNumber);
		return revision;
	}

	/**
	 * "Enciclopedia" (Step 5): every `REVIEWED` candidate awaiting publish, plus every
	 * currently-published item that doesn't already have one (a `REVIEWED` sibling always
	 * takes priority for display - it is the thing a PUBLISHER needs to act on next).
	 * `RETIRED` items are omitted entirely: nothing produces that state until Step 8.
	 */
	public List<EncyclopediaEntry> listEncyclopedia() {
		var reviewed = this.workingRevisionRepository.findByStatusOrderByUpdatedAtAsc(WorkingRevisionStatus.REVIEWED);
		var reviewedItemIds = reviewed.stream().map(WorkingRevisionEntity::getItemId).collect(Collectors.toSet());

		List<EncyclopediaEntry> entries = new ArrayList<>();
		for (var revision : reviewed) {
			entries.add(new EncyclopediaEntry.ReviewedCandidate(revision, translationsOf(revision.getId())));
		}
		for (var item : this.itemRepository.findByLiveVersionIdIsNotNull()) {
			if (reviewedItemIds.contains(item.getId())) {
				continue;
			}
			var version = this.contentVersionRepository.findById(item.getLiveVersionId()).orElseThrow();
			var translations = this.contentVersionTranslationRepository.findByIdContentVersionId(version.getId());
			entries.add(new EncyclopediaEntry.PublishedItem(version, translations));
		}
		return entries;
	}

	public EncyclopediaEntry getEncyclopediaItem(UUID itemId) {
		var reviewedCandidate = this.workingRevisionRepository.findByItemIdAndStatus(itemId,
				WorkingRevisionStatus.REVIEWED);
		if (reviewedCandidate.isPresent()) {
			var revision = reviewedCandidate.get();
			return new EncyclopediaEntry.ReviewedCandidate(revision, translationsOf(revision.getId()));
		}

		var item = this.itemRepository.findById(itemId)
			.orElseThrow(() -> new EncyclopediaItemNotFoundException(itemId));
		if (item.getLiveVersionId() == null) {
			throw new EncyclopediaItemNotFoundException(itemId);
		}
		var version = this.contentVersionRepository.findById(item.getLiveVersionId()).orElseThrow();
		var translations = this.contentVersionTranslationRepository.findByIdContentVersionId(version.getId());
		return new EncyclopediaEntry.PublishedItem(version, translations);
	}

	public WorkingRevisionEntity getOwnDraft(UUID workingRevisionId, UUID authorId) {
		return ownWorkingRevisionOrThrow(workingRevisionId, authorId);
	}

	/**
	 * The shared review queue (UF-CNT-13+): every working revision currently `IN_REVIEW`.
	 */
	public List<WorkingRevisionEntity> reviewQueue() {
		return this.workingRevisionRepository.findByStatusOrderByUpdatedAtAsc(WorkingRevisionStatus.IN_REVIEW);
	}

	public WorkingRevisionEntity getForReview(UUID workingRevisionId) {
		return workingRevisionOrThrow(workingRevisionId);
	}

	public List<TranslationEntity> translationsOf(UUID workingRevisionId) {
		return this.translationRepository.findByIdWorkingRevisionId(workingRevisionId);
	}

	public List<WorkingRevisionEntity> listOwnDrafts(UUID authorId) {
		return this.workingRevisionRepository.findByAuthorIdAndStatusIn(authorId,
				List.of(WorkingRevisionStatus.DRAFT, WorkingRevisionStatus.IN_REVIEW));
	}

	private WorkingRevisionEntity ownWorkingRevisionOrThrow(UUID workingRevisionId, UUID authorId) {
		return this.workingRevisionRepository.findByIdAndAuthorId(workingRevisionId, authorId)
			.orElseThrow(() -> new WorkingRevisionNotFoundException(workingRevisionId));
	}

	private WorkingRevisionEntity workingRevisionOrThrow(UUID workingRevisionId) {
		return this.workingRevisionRepository.findById(workingRevisionId)
			.orElseThrow(() -> new WorkingRevisionNotFoundException(workingRevisionId));
	}

	private static void requireStatus(WorkingRevisionEntity revision, WorkingRevisionStatus required, String action) {
		if (revision.getStatus() != required) {
			throw new InvalidStatusTransitionException(revision.getStatus(), action);
		}
	}

	private static void requireClaimant(WorkingRevisionEntity revision, UUID reviewerId, String action) {
		if (!reviewerId.equals(revision.getClaimedBy())) {
			throw new NotClaimantException(action);
		}
	}

	private static void clearClaim(WorkingRevisionEntity revision) {
		revision.setClaimedBy(null);
		revision.setClaimedByEmail(null);
	}

	/**
	 * 4.1's supersession rule: a later approval displaces an item's existing active
	 * candidate.
	 */
	private void supersedeReviewedSibling(WorkingRevisionEntity revision, UUID reviewerId, String reviewerEmail) {
		for (var sibling : this.workingRevisionRepository.findByItemIdAndStatusAndIdNot(revision.getItemId(),
				WorkingRevisionStatus.REVIEWED, revision.getId())) {
			sibling.setStatus(WorkingRevisionStatus.SUPERSEDED);
			sibling.setUpdatedAt(this.clock.instant());
			this.auditLogService.record(AUDIT_ACTION_SUPERSEDE, reviewerId, reviewerEmail, sibling.getItemId(),
					sibling.getRomaji(), "Superseded by working revision " + revision.getId());
		}
	}

	private void upsertTranslation(UUID workingRevisionId, String languageCode, TranslationRequest request) {
		var id = new TranslationId(workingRevisionId, languageCode);
		var existing = this.translationRepository.findById(id);
		if (existing.isPresent()) {
			existing.get().setName(request.name());
			existing.get().setDescription(request.description());
		}
		else {
			this.translationRepository
				.save(new TranslationEntity(workingRevisionId, languageCode, request.name(), request.description()));
		}
	}

}
