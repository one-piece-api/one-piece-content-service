package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.persistence.repository.ContentVersionTranslationRepository;
import dev.onepieceapi.contentservice.persistence.entity.LanguageEntity;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.TranslationRepository;
import dev.onepieceapi.contentservice.service.exception.InvalidLanguageCodeException;
import dev.onepieceapi.contentservice.service.exception.InvalidLanguageNameException;
import dev.onepieceapi.contentservice.service.exception.LanguageAlreadyExistsException;
import dev.onepieceapi.contentservice.service.exception.LanguageInUseException;
import dev.onepieceapi.contentservice.service.exception.LanguageNotFoundException;
import dev.onepieceapi.contentservice.service.validation.ContentValidator;
import dev.onepieceapi.contentservice.web.dto.LanguageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ADMIN-managed language catalog CRUD
 * (docs/user-flows/authentication-and-user-management.md 3.2,
 * docs/implementation-plan-content.md Step 10) - config, not editorial content, so it
 * lives outside {@link DevilFruitTypeService} and is gated on {@code languages:manage}
 * rather than any {@code content:*} permission. Every active language is already read
 * dynamically by {@link ContentValidator} and {@code DevilFruitTypeResponseMapper} via
 * its code, so adding one here needs no further code change for it to become required on
 * the next submission (3.2's own stated consequence) - this service only owns the catalog
 * rows themselves.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class LanguageService {

	private static final String AUDIT_ACTION_CREATE = "LANGUAGE_CREATED";

	private static final String AUDIT_ACTION_DELETE = "LANGUAGE_DELETED";

	/** Exactly two lowercase ASCII letters - ISO 639-1 style, e.g. "fr", "es". */
	private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z]{2}$");

	private static final int MAX_NAME_LENGTH = 100;

	private final LanguageRepository languageRepository;

	private final TranslationRepository translationRepository;

	private final ContentVersionTranslationRepository contentVersionTranslationRepository;

	private final AuditLogService auditLogService;

	public List<LanguageResponse> list() {
		return this.languageRepository.findAllByOrderByCode().stream().map(LanguageService::toResponse).toList();
	}

	@Transactional
	public LanguageResponse create(String rawCode, String rawName, UUID actorId, String actorEmail) {
		var code = normalizeCode(rawCode);
		if (!CODE_PATTERN.matcher(code).matches()) {
			throw new InvalidLanguageCodeException(rawCode);
		}
		var name = rawName == null ? "" : rawName.trim();
		if (name.isBlank() || name.length() > MAX_NAME_LENGTH) {
			throw new InvalidLanguageNameException();
		}
		if (this.languageRepository.existsById(code)) {
			throw new LanguageAlreadyExistsException(code);
		}
		var language = this.languageRepository.save(new LanguageEntity(code, name));
		this.auditLogService.record(AUDIT_ACTION_CREATE, actorId, actorEmail, null, code, name);
		return toResponse(language);
	}

	@Transactional
	public void delete(String rawCode, UUID actorId, String actorEmail) {
		var code = normalizeCode(rawCode);
		var language = this.languageRepository.findById(code).orElseThrow(() -> new LanguageNotFoundException(code));
		if (this.translationRepository.existsByIdLanguageCode(code)
				|| this.contentVersionTranslationRepository.existsByIdLanguageCode(code)) {
			throw new LanguageInUseException(code);
		}
		this.languageRepository.delete(language);
		this.auditLogService.record(AUDIT_ACTION_DELETE, actorId, actorEmail, null, code, language.getName());
	}

	private static LanguageResponse toResponse(LanguageEntity language) {
		return new LanguageResponse(language.getCode(), language.getName());
	}

	private static String normalizeCode(String rawCode) {
		return rawCode == null ? "" : rawCode.trim().toLowerCase(Locale.ROOT);
	}

}
