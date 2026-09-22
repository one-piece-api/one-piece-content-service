package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.persistence.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.TranslationRepository;
import dev.onepieceapi.contentservice.persistence.WorkingRevisionEntity;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every content-shape rule {@link DevilFruitTypeService} enforces, in one place: is a
 * draft complete enough to submit (UF-CNT-03), does an edit reference only known
 * languages (3.2), is a rejection reason actually present (UF-CNT-06). Deliberately plain
 * methods on an injected collaborator, not Bean Validation annotations - these checks
 * read the database (the language catalog, the working revision's own saved
 * translations), which {@code @Valid}/{@code ConstraintValidator} is not a good fit for:
 * it validates the shape of an object already in memory, not invariants that depend on
 * looking something up first. A {@code ConstraintValidator} that injects a repository to
 * do this would just hide the same database-dependent business logic behind an annotation
 * instead of making it explicit here.
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

	private final TranslationRepository translationRepository;

	private final LanguageRepository languageRepository;

	/**
	 * UF-CNT-03: every active language's {@code name}/{@code description} must be
	 * non-blank and within its length limit, and {@code romaji} likewise - a draft may be
	 * saved incomplete/over-length, but not submitted that way.
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
