package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.persistence.entity.LanguageEntity;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.exception.TranslationLanguageUnknownException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The rules a Devil Fruit Type must meet to be saved as a draft
 * (docs/user-flows/content-editorial-workflow.md 3.3): only languages of the catalog, and
 * a romaji and names no other content has. A draft may be incomplete - what a version
 * needs to be submitted is checked when it is.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitTypeValidator {

	private static final String ROMAJI_FIELD = "romaji";

	/** Named as request validation names it, so a client reads both the same way. */
	private static final String NAME_FIELD = "translations[%s].name";

	private static final String ALREADY_USED = "is already used by another content";

	private final DevilFruitTypeVersionRepository versionRepository;

	private final LanguageRepository languageRepository;

	/**
	 * @param contentId the content being saved, whose own versions never collide with
	 * each other
	 */
	public void validateDraft(UUID contentId, DevilFruitType body) {
		requireKnownLanguages(body);
		requireUniqueValues(contentId, body);
	}

	private void requireKnownLanguages(DevilFruitType body) {
		Set<String> catalog = this.languageRepository.findAll()
			.stream()
			.map(LanguageEntity::getCode)
			.collect(Collectors.toSet());
		List<String> unknown = body.translations().keySet().stream().filter(code -> !catalog.contains(code)).toList();
		if (!unknown.isEmpty()) {
			throw new TranslationLanguageUnknownException(unknown);
		}
	}

	/** Every value at fault is reported, not just the first one found. */
	private void requireUniqueValues(UUID contentId, DevilFruitType body) {
		List<FieldViolation> taken = new ArrayList<>();
		if (romajiIsTaken(contentId, body.romaji())) {
			taken.add(new FieldViolation(ROMAJI_FIELD, ALREADY_USED));
		}
		body.translations().forEach((language, translation) -> {
			if (nameIsTaken(contentId, language, translation)) {
				taken.add(new FieldViolation(NAME_FIELD.formatted(language), ALREADY_USED));
			}
		});
		if (!taken.isEmpty()) {
			throw new ValueAlreadyUsedException(taken);
		}
	}

	private boolean romajiIsTaken(UUID contentId, String romaji) {
		return romaji != null && this.versionRepository.romajiIsTakenByAnother(romaji, contentId);
	}

	private boolean nameIsTaken(UUID contentId, String language, DevilFruitTypeTranslation translation) {
		String name = translation.name();
		return name != null && this.versionRepository.nameIsTakenByAnother(language, name, contentId);
	}

}
