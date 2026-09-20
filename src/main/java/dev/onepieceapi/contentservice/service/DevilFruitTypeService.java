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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UF-CNT-01/02: create and edit a private working revision. Ownership (4.1/7.5 - a draft
 * is visible only to its own author) is enforced here, not by {@code SecuredEndpoint},
 * which only knows about the coarse {@code content:write} permission, not who owns which
 * row.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitTypeService {

	private static final String AUDIT_ACTION_CREATE = "DEVIL_FRUIT_TYPE_DRAFT_CREATED";

	private static final String AUDIT_ACTION_EDIT = "DEVIL_FRUIT_TYPE_DRAFT_EDITED";

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
		var revision = ownDraftOrThrow(workingRevisionId, authorId);

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

	public WorkingRevisionEntity getOwnDraft(UUID workingRevisionId, UUID authorId) {
		return ownDraftOrThrow(workingRevisionId, authorId);
	}

	public List<TranslationEntity> translationsOf(UUID workingRevisionId) {
		return this.translationRepository.findByIdWorkingRevisionId(workingRevisionId);
	}

	public List<WorkingRevisionEntity> listOwnDrafts(UUID authorId) {
		return this.workingRevisionRepository.findByAuthorIdAndStatusIn(authorId,
				List.of(WorkingRevisionStatus.DRAFT, WorkingRevisionStatus.IN_REVIEW));
	}

	private WorkingRevisionEntity ownDraftOrThrow(UUID workingRevisionId, UUID authorId) {
		return this.workingRevisionRepository.findByIdAndAuthorId(workingRevisionId, authorId)
			.orElseThrow(() -> new WorkingRevisionNotFoundException(workingRevisionId));
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
