package dev.onepieceapi.contentservice.service.language;

import dev.onepieceapi.contentservice.service.audit.AuditLogService;

import dev.onepieceapi.contentservice.domain.language.Language;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.persistence.mapper.LanguageMapper;
import dev.onepieceapi.contentservice.persistence.repository.VersionBodyRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.exception.LanguageAlreadyExistsException;
import dev.onepieceapi.contentservice.service.exception.LanguageInUseException;
import dev.onepieceapi.contentservice.service.exception.LanguageNotFoundException;
import dev.onepieceapi.contentservice.service.validation.LanguageValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * ADMIN-managed language catalog CRUD (docs/user-flows/content-editorial-workflow.md 3.2)
 * - system configuration, not editorial content, so it is gated on
 * {@code languages:manage} rather than any {@code content:*} permission.
 */
@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class LanguageService {

	private static final String AUDIT_ACTION_CREATE = "LANGUAGE_CREATED";

	private static final String AUDIT_ACTION_DELETE = "LANGUAGE_DELETED";

	private final LanguageRepository languageRepository;

	/**
	 * The versions of every entity: a language is in use if any of them says something in
	 * it.
	 */
	private final List<VersionBodyRepository<?>> versionRepositories;

	private final AuditLogService auditLogService;

	private final LanguageValidator languageValidator;

	public List<Language> list() {
		return this.languageRepository.findAllByOrderByCode().stream().map(LanguageMapper::toDomain).toList();
	}

	@Transactional
	public Language create(String rawCode, String rawName, User actor) {
		var language = new Language(normalizeCode(rawCode), normalizeName(rawName));
		this.languageValidator.validate(language);
		if (this.languageRepository.existsById(language.code())) {
			throw new LanguageAlreadyExistsException(language.code());
		}
		var saved = this.languageRepository.save(LanguageMapper.toEntity(language));
		this.auditLogService.record(AUDIT_ACTION_CREATE, actor, null, language.code(), language.name());
		return LanguageMapper.toDomain(saved);
	}

	@Transactional
	public void delete(String rawCode, User actor) {
		var code = normalizeCode(rawCode);
		var language = this.languageRepository.findById(code).orElseThrow(() -> new LanguageNotFoundException(code));
		if (this.versionRepositories.stream().anyMatch(repository -> repository.existsByLanguage(code))) {
			throw new LanguageInUseException(code);
		}
		this.languageRepository.delete(language);
		this.auditLogService.record(AUDIT_ACTION_DELETE, actor, null, code, language.getName());
	}

	private static String normalizeCode(String rawCode) {
		return rawCode == null ? "" : rawCode.trim().toLowerCase(Locale.ROOT);
	}

	private static String normalizeName(String rawName) {
		return rawName == null ? "" : rawName.trim();
	}

}
