package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.persistence.DevilFruitTypeItemEntity;
import dev.onepieceapi.contentservice.persistence.DevilFruitTypeItemRepository;
import dev.onepieceapi.contentservice.persistence.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.TranslationEntity;
import dev.onepieceapi.contentservice.persistence.TranslationId;
import dev.onepieceapi.contentservice.persistence.TranslationRepository;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionEntity;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionRepository;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionStatus;
import dev.onepieceapi.contentservice.web.dto.TranslationRequest;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UF-CNT-01/02/03/04: create/edit a private working revision, submit it for review, or
 * withdraw it back to draft. Ownership (4.1/7.5 - a draft is visible only to its own
 * author) is enforced here, not by {@code SecuredEndpoint}, which only knows about the
 * coarse {@code content:write} permission, not who owns which row.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitTypeService {

	/**
	 * Submission length limits (3.1) - not enforced at the database level on purpose, see
	 * V3's migration comment.
	 */
	private static final int MAX_ROMAJI_LENGTH = 100;

	private static final int MAX_NAME_LENGTH = 100;

	private static final int MAX_DESCRIPTION_LENGTH = 2000;

	private static final String AUDIT_ACTION_CREATE = "DEVIL_FRUIT_TYPE_DRAFT_CREATED";

	private static final String AUDIT_ACTION_EDIT = "DEVIL_FRUIT_TYPE_DRAFT_EDITED";

	private static final String AUDIT_ACTION_SUBMIT = "DEVIL_FRUIT_TYPE_SUBMITTED_FOR_REVIEW";

	private static final String AUDIT_ACTION_WITHDRAW = "DEVIL_FRUIT_TYPE_WITHDRAWN_TO_DRAFT";

	private final DevilFruitTypeItemRepository itemRepository;

	private final WorkingRevisionRepository workingRevisionRepository;

	private final TranslationRepository translationRepository;

	private final LanguageRepository languageRepository;

	private final AuditLogService auditLogService;

	private final Clock clock;

	@Transactional
	public WorkingRevisionEntity createDraft(UUID authorId, String authorEmail) {
		var now = this.clock.instant();
		var item = this.itemRepository.save(new DevilFruitTypeItemEntity(UUID.randomUUID(), now));
		var revision = this.workingRevisionRepository.save(
				new WorkingRevisionEntity(UUID.randomUUID(), item.getId(), authorId, WorkingRevisionStatus.DRAFT, now));
		this.auditLogService.record(AUDIT_ACTION_CREATE, authorId, authorEmail, item.getId(), null, null);
		return revision;
	}

	@Transactional
	public WorkingRevisionEntity updateDraft(UUID workingRevisionId, UUID authorId, String authorEmail, String romaji,
			Map<String, TranslationRequest> translations) {
		var revision = ownWorkingRevisionOrThrow(workingRevisionId, authorId);

		var requestedLanguages = translations == null ? Set.<String>of() : translations.keySet();
		rejectUnknownLanguages(requestedLanguages);

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
		validateCompleteForSubmission(revision);
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

		revision.setStatus(WorkingRevisionStatus.DRAFT);
		revision.setUpdatedAt(this.clock.instant());
		this.auditLogService.record(AUDIT_ACTION_WITHDRAW, authorId, authorEmail, revision.getItemId(),
				revision.getRomaji(), null);
		return revision;
	}

	public WorkingRevisionEntity getOwnDraft(UUID workingRevisionId, UUID authorId) {
		return ownWorkingRevisionOrThrow(workingRevisionId, authorId);
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

	private static void requireStatus(WorkingRevisionEntity revision, WorkingRevisionStatus required, String action) {
		if (revision.getStatus() != required) {
			throw new InvalidStatusTransitionException(revision.getStatus(), action);
		}
	}

	/**
	 * UF-CNT-03: every active language's {@code name}/{@code description} must be
	 * non-blank and within its length limit, and {@code romaji} likewise - a draft may be
	 * saved incomplete/over-length, but not submitted that way.
	 */
	private void validateCompleteForSubmission(WorkingRevisionEntity revision) {
		List<FieldViolation> violations = new ArrayList<>();
		checkField(violations, "romaji", revision.getRomaji(), MAX_ROMAJI_LENGTH);

		var translationsByLanguage = this.translationRepository.findByIdWorkingRevisionId(revision.getId())
			.stream()
			.collect(Collectors.toMap(t -> t.getId().getLanguageCode(), t -> t));
		for (var language : this.languageRepository.findAll()) {
			var translation = translationsByLanguage.get(language.getCode());
			String name = translation != null ? translation.getName() : null;
			String description = translation != null ? translation.getDescription() : null;
			checkField(violations, "translations." + language.getCode() + ".name", name, MAX_NAME_LENGTH);
			checkField(violations, "translations." + language.getCode() + ".description", description,
					MAX_DESCRIPTION_LENGTH);
		}

		if (!violations.isEmpty()) {
			throw new IncompleteContentException(violations);
		}
	}

	private static void checkField(List<FieldViolation> violations, String field, String value, int maxLength) {
		if (value == null || value.isBlank()) {
			violations.add(new FieldViolation(field, "required"));
		}
		else if (value.length() > maxLength) {
			violations.add(new FieldViolation(field, "must be at most " + maxLength + " characters"));
		}
	}

	private void rejectUnknownLanguages(Set<String> requestedLanguages) {
		if (requestedLanguages.isEmpty()) {
			return;
		}
		var known = this.languageRepository.findAllByCodeIn(List.copyOf(requestedLanguages))
			.stream()
			.map(l -> l.getCode())
			.collect(Collectors.toSet());
		var unknown = new HashSet<>(requestedLanguages);
		unknown.removeAll(known);
		if (!unknown.isEmpty()) {
			throw new UnknownLanguageException(unknown);
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
