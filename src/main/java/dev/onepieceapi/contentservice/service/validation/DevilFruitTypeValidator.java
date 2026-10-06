package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.persistence.entity.LanguageEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.exception.SlugAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.TranslationLanguageUnknownException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionIdenticalException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The rules a Devil Fruit Type must meet (docs/user-flows/content-editorial-workflow.md
 * 3.3). To be saved as a draft: only languages of the catalog, a romaji that gives a
 * slug, and a slug and names no other content has - a draft may be incomplete. To be
 * submitted, in this order, stopping at the first rule broken: complete in every language
 * of the catalog, a romaji that gives a slug, still unique, and different from every
 * other version of its content.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitTypeValidator {

	private static final String ROMAJI_FIELD = "romaji";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String NAME_FIELD = "translations[%s].name";

	private static final String DESCRIPTION_FIELD = "translations[%s].description";

	private static final String ADVANTAGES_FIELD = "translations[%s].advantages";

	private static final String DISADVANTAGES_FIELD = "translations[%s].disadvantages";

	private static final String ALREADY_USED = "is already used by another content";

	private static final String REQUIRED = "is required for review";

	private static final String NO_SLUG = "must contain a letter or a digit";

	private static final String SLUG_TAKEN = "gives the public address of another content";

	private final DevilFruitTypeVersionRepository versionRepository;

	private final LanguageRepository languageRepository;

	/**
	 * @param contentId the content being saved, whose own versions never collide with
	 * each other
	 */
	public void validateDraft(UUID contentId, DevilFruitType body) {
		requireKnownLanguages(body);
		requireUniqueValues(contentId, body, slugOf(body));
	}

	/**
	 * Length needs no check here: it is enforced on every save, and nothing longer could
	 * have been stored. The languages are known for the same reason.
	 * @param versionNumber the version being submitted, the one not compared with itself
	 */
	public void validateSubmission(UUID contentId, int versionNumber, DevilFruitType body) {
		requireComplete(body);
		requireUniqueValues(contentId, body, slugOf(body));
		requireDifferentFromOtherVersions(contentId, versionNumber, body);
	}

	private void requireKnownLanguages(DevilFruitType body) {
		Set<String> catalog = catalogCodes();
		List<String> unknown = body.translations().keySet().stream().filter(code -> !catalog.contains(code)).toList();
		if (!unknown.isEmpty()) {
			throw new TranslationLanguageUnknownException(unknown);
		}
	}

	/**
	 * The romaji, then a name, a description, advantages and disadvantages per language -
	 * every one missing.
	 */
	private void requireComplete(DevilFruitType body) {
		List<FieldViolation> missing = new ArrayList<>();
		if (body.romaji() == null) {
			missing.add(new FieldViolation(ROMAJI_FIELD, REQUIRED));
		}
		for (String language : catalogCodes()) {
			DevilFruitTypeTranslation translation = body.translationIn(language);
			if (translation.name() == null) {
				missing.add(new FieldViolation(NAME_FIELD.formatted(language), REQUIRED));
			}
			if (translation.description() == null) {
				missing.add(new FieldViolation(DESCRIPTION_FIELD.formatted(language), REQUIRED));
			}
			if (translation.advantages() == null) {
				missing.add(new FieldViolation(ADVANTAGES_FIELD.formatted(language), REQUIRED));
			}
			if (translation.disadvantages() == null) {
				missing.add(new FieldViolation(DISADVANTAGES_FIELD.formatted(language), REQUIRED));
			}
		}
		if (!missing.isEmpty()) {
			throw new VersionIncompleteException(missing);
		}
	}

	/** Compares what is stored - values without space around them - case included. */
	private void requireDifferentFromOtherVersions(UUID contentId, int versionNumber, DevilFruitType body) {
		this.versionRepository.findOthers(contentId, versionNumber)
			.stream()
			.map(DevilFruitTypeVersionMapper::toDomain)
			.filter(other -> other.says(body))
			.findFirst()
			.ifPresent(identical -> {
				throw new VersionIdenticalException(identical.number());
			});
	}

	/**
	 * The slug of the romaji - the public address of the content - refused when the
	 * romaji gives none; empty while no romaji is written.
	 */
	private Optional<String> slugOf(DevilFruitType body) {
		if (body.romaji() == null) {
			return Optional.empty();
		}
		return Optional.of(this.versionRepository.slugOf(body.romaji())
			.orElseThrow(() -> new ValueInvalidException(List.of(new FieldViolation(ROMAJI_FIELD, NO_SLUG)))));
	}

	/**
	 * Every value equal to another content's is reported, not just the first one found.
	 * Then the slug: a romaji no other content has may still give the address of another
	 * one (a macron is all that sets Ryu apart) - said apart, since there is no equal
	 * value to show.
	 */
	private void requireUniqueValues(UUID contentId, DevilFruitType body, Optional<String> slug) {
		requireUnusedValues(contentId, body);
		slug.filter(value -> this.versionRepository.slugIsTakenByAnother(value, contentId)).ifPresent(value -> {
			throw new SlugAlreadyUsedException(value, List.of(new FieldViolation(ROMAJI_FIELD, SLUG_TAKEN)));
		});
	}

	private void requireUnusedValues(UUID contentId, DevilFruitType body) {
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

	/** The language codes of the catalog, in order. */
	private Set<String> catalogCodes() {
		return this.languageRepository.findAll()
			.stream()
			.map(LanguageEntity::getCode)
			.collect(Collectors.toCollection(TreeSet::new));
	}

}
