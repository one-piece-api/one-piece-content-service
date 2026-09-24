package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionTranslationRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEntity;
import dev.onepieceapi.contentservice.persistence.repository.TranslationRepository;
import dev.onepieceapi.contentservice.persistence.entity.WorkingRevisionEntity;
import dev.onepieceapi.contentservice.persistence.repository.WorkingRevisionRepository;
import dev.onepieceapi.contentservice.persistence.entity.WorkingRevisionStatus;
import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.service.exception.DuplicateContentException;
import dev.onepieceapi.contentservice.service.exception.IdenticalToExistingVersionException;
import dev.onepieceapi.contentservice.service.exception.IncompleteContentException;
import dev.onepieceapi.contentservice.service.exception.MissingRejectionReasonException;
import dev.onepieceapi.contentservice.service.exception.UnknownLanguageException;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every content-shape rule {@link DevilFruitTypeService} enforces, in one place: is a
 * draft complete enough to submit (UF-CNT-03), does its romaji/name collide with another
 * item's (3.3), does it actually change anything from this same item's own publish
 * history (3.3, user-reported gap), does an edit reference only known languages (3.2), is
 * a rejection reason actually present (UF-CNT-06). Deliberately plain methods on an
 * injected collaborator, not Bean Validation annotations - these checks read the database
 * (the language catalog, the working revision's own saved translations, every other
 * item's reserving content, this item's own version history), which
 * {@code @Valid}/{@code ConstraintValidator} is not a good fit for: it validates the
 * shape of an object already in memory, not invariants that depend on looking something
 * up first. A {@code ConstraintValidator} that injects a repository to do this would just
 * hide the same database-dependent business logic behind an annotation instead of making
 * it explicit here.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class ContentValidator {

	/**
	 * Submission length limits (3.1) - not enforced at the database level on purpose, see
	 * V3's migration comment.
	 */
	private static final int MAX_ROMAJI_LENGTH = 100;

	private static final int MAX_NAME_LENGTH = 100;

	private static final int MAX_DESCRIPTION_LENGTH = 2000;

	private static final String ALREADY_USED_MESSAGE = "already used by another Devil Fruit Type";

	/**
	 * Which working-revision statuses "reserve" a romaji/name against every other item
	 * (3.3): {@code IN_REVIEW}/{@code REVIEWED} because the shared queue already makes
	 * them visible beyond their own author, {@code PUBLISHED} because it stays the status
	 * of a working revision even after its item is later retired (Step 8 only clears the
	 * item's live pointer, never this status) - so a retired item still reserves its
	 * name. Deliberately excludes {@code DRAFT}: checking against another author's
	 * still-private draft would leak its existence, breaking 7.5's total isolation. Also
	 * excludes {@code SUPERSEDED}: out of the active pipeline, same reasoning as
	 * everywhere else it is treated that way.
	 */
	private static final List<WorkingRevisionStatus> RESERVING_STATUSES = List.of(WorkingRevisionStatus.IN_REVIEW,
			WorkingRevisionStatus.REVIEWED, WorkingRevisionStatus.PUBLISHED);

	private final TranslationRepository translationRepository;

	private final LanguageRepository languageRepository;

	private final WorkingRevisionRepository workingRevisionRepository;

	private final ContentVersionRepository contentVersionRepository;

	private final ContentVersionTranslationRepository contentVersionTranslationRepository;

	/**
	 * UF-CNT-03: every active language's {@code name}/{@code description} must be
	 * non-blank and within its length limit, and {@code romaji} likewise - a draft may be
	 * saved incomplete/over-length, but not submitted that way. Once complete, 3.3's two
	 * uniqueness rules are checked next (see {@link #requireUniqueForSubmission} and
	 * {@link #requireDifferentFromExistingVersions}) - there is no point flagging a
	 * still-blank field as a collision too.
	 */
	public void requireCompleteForSubmission(WorkingRevisionEntity revision) {
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

		requireUniqueForSubmission(revision, translationsByLanguage);
		requireDifferentFromExistingVersions(revision, translationsByLanguage);
	}

	/**
	 * 3.3: neither romaji nor a per-language name may collide with another item's - see
	 * {@link #RESERVING_STATUSES} for exactly which of that other item's content counts,
	 * and why a resubmission of this same item's own romaji/name is never flagged (only
	 * <em>other</em> items are checked).
	 */
	private void requireUniqueForSubmission(WorkingRevisionEntity revision,
			Map<String, TranslationEntity> translationsByLanguage) {
		List<FieldViolation> violations = new ArrayList<>();
		if (this.workingRevisionRepository.existsByRomajiIgnoreCaseAndStatusInAndItemIdNot(revision.getRomaji(),
				RESERVING_STATUSES, revision.getItemId())) {
			violations.add(new FieldViolation("romaji", ALREADY_USED_MESSAGE));
		}

		var reservingWorkingRevisionIds = this.workingRevisionRepository
			.findByStatusInAndItemIdNot(RESERVING_STATUSES, revision.getItemId())
			.stream()
			.map(WorkingRevisionEntity::getId)
			.toList();
		if (!reservingWorkingRevisionIds.isEmpty()) {
			for (var language : this.languageRepository.findAll()) {
				var name = translationsByLanguage.get(language.getCode()).getName();
				if (this.translationRepository.existsByIdLanguageCodeAndNameIgnoreCaseAndIdWorkingRevisionIdIn(
						language.getCode(), name, reservingWorkingRevisionIds)) {
					violations
						.add(new FieldViolation("translations." + language.getCode() + ".name", ALREADY_USED_MESSAGE));
				}
			}
		}

		if (!violations.isEmpty()) {
			throw new DuplicateContentException(violations);
		}
	}

	/**
	 * 3.3 (user-reported gap): a submission on an item that already has publish history
	 * must actually change something from every version ever published for it, not just
	 * the current live one - otherwise nothing stops an author from submitting, and a
	 * PUBLISHER from approving/publishing, several byte-for-byte identical versions in a
	 * row. A brand-new item has no history yet, so this is a no-op for it.
	 */
	private void requireDifferentFromExistingVersions(WorkingRevisionEntity revision,
			Map<String, TranslationEntity> translationsByLanguage) {
		for (var version : this.contentVersionRepository.findByItemIdOrderBySequenceNumberDesc(revision.getItemId())) {
			if (isIdenticalToVersion(revision, translationsByLanguage, version)) {
				throw new IdenticalToExistingVersionException(version.getSequenceNumber());
			}
		}
	}

	private boolean isIdenticalToVersion(WorkingRevisionEntity revision,
			Map<String, TranslationEntity> translationsByLanguage, ContentVersionEntity version) {
		if (!Objects.equals(revision.getRomaji(), version.getRomaji())) {
			return false;
		}
		var versionTranslationsByLanguage = this.contentVersionTranslationRepository
			.findByIdContentVersionId(version.getId())
			.stream()
			.collect(Collectors.toMap(t -> t.getId().getLanguageCode(), t -> t));
		for (var language : this.languageRepository.findAll()) {
			var newTranslation = translationsByLanguage.get(language.getCode());
			var oldTranslation = versionTranslationsByLanguage.get(language.getCode());
			String newName = newTranslation != null ? newTranslation.getName() : null;
			String newDescription = newTranslation != null ? newTranslation.getDescription() : null;
			String oldName = oldTranslation != null ? oldTranslation.getName() : null;
			String oldDescription = oldTranslation != null ? oldTranslation.getDescription() : null;
			if (!Objects.equals(newName, oldName) || !Objects.equals(newDescription, oldDescription)) {
				return false;
			}
		}
		return true;
	}

	/** 3.2: every language an edit touches must already be in the active catalog. */
	public void requireKnownLanguages(Set<String> requestedLanguages) {
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

	/** UF-CNT-06: a rejection must always explain what the author needs to correct. */
	public void requireReason(String reason) {
		if (reason == null || reason.isBlank()) {
			throw new MissingRejectionReasonException();
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

}
