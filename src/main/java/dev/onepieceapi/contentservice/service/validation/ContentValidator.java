package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.LanguageEntity;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.VersionBodyRepository;
import dev.onepieceapi.contentservice.service.exception.SlugAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.TranslationLanguageUnknownException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionIdenticalException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The rules every content must meet, whatever its entity
 * (docs/user-flows/content-editorial-workflow.md 3.3), each checked among the contents of
 * the same entity. To be saved as a draft: only languages of the catalog, a romaji that
 * gives a slug, and a slug and names no other content has - a draft may be incomplete. To
 * be submitted, in this order, stopping at the first rule broken: complete in every
 * language of the catalog (what is required is the entity's to say, see
 * {@link ContentBody#missingFields}), a romaji that gives a slug, still unique, and
 * different from every other version of its content. Each entity has its own, extending
 * this one, e.g. {@link DevilFruitTypeValidator}.
 *
 * @param <T> what a version of the entity says
 * @param <E> the entity's version table
 */
@RequiredArgsConstructor
public class ContentValidator<T extends ContentBody<T>, E extends VersionBodyEntity> {

	private static final String ROMAJI_FIELD = "romaji";

	/** Named as request validation names them, so a client reads both the same way. */
	private static final String NAME_FIELD = "translations[%s].name";

	private static final String ALREADY_USED = "is already used by another content";

	private static final String REQUIRED = "is required for review";

	private static final String NO_SLUG = "must contain a letter or a digit";

	private static final String SLUG_TAKEN = "gives the public address of another content";

	private final VersionBodyRepository<E> versionRepository;

	private final LanguageRepository languageRepository;

	/** How a stored version reads, to compare it with the one submitted. */
	private final Function<E, Version<T>> toDomain;

	/**
	 * @param contentId the content being saved, whose own versions never collide with
	 * each other
	 */
	public void validateDraft(UUID contentId, T body) {
		requireKnownLanguages(body);
		requireUniqueValues(contentId, body, slugOf(body));
	}

	/**
	 * Length needs no check here: it is enforced on every save, and nothing longer could
	 * have been stored. The languages are known for the same reason.
	 * @param versionNumber the version being submitted, the one not compared with itself
	 */
	public void validateSubmission(UUID contentId, int versionNumber, T body) {
		requireComplete(body);
		requireUniqueValues(contentId, body, slugOf(body));
		requireDifferentFromOtherVersions(contentId, versionNumber, body);
	}

	private void requireKnownLanguages(T body) {
		Set<String> catalog = catalogCodes();
		List<String> unknown = body.languages().stream().filter(code -> !catalog.contains(code)).toList();
		if (!unknown.isEmpty()) {
			throw new TranslationLanguageUnknownException(unknown);
		}
	}

	private void requireComplete(T body) {
		List<FieldViolation> missing = body.missingFields(catalogCodes())
			.stream()
			.map(field -> new FieldViolation(field, REQUIRED))
			.toList();
		if (!missing.isEmpty()) {
			throw new VersionIncompleteException(missing);
		}
	}

	/** Compares what is stored - values without space around them - case included. */
	private void requireDifferentFromOtherVersions(UUID contentId, int versionNumber, T body) {
		this.versionRepository.findOthers(contentId, versionNumber)
			.stream()
			.map(this.toDomain)
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
	private Optional<String> slugOf(T body) {
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
	private void requireUniqueValues(UUID contentId, T body, Optional<String> slug) {
		requireUnusedValues(contentId, body);
		slug.filter(value -> this.versionRepository.slugIsTakenByAnother(value, contentId)).ifPresent(value -> {
			throw new SlugAlreadyUsedException(value, List.of(new FieldViolation(ROMAJI_FIELD, SLUG_TAKEN)));
		});
	}

	private void requireUnusedValues(UUID contentId, T body) {
		List<FieldViolation> taken = new ArrayList<>();
		if (romajiIsTaken(contentId, body.romaji())) {
			taken.add(new FieldViolation(ROMAJI_FIELD, ALREADY_USED));
		}
		body.names().forEach((language, name) -> {
			if (this.versionRepository.nameIsTakenByAnother(language, name, contentId)) {
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

	/** The language codes of the catalog, in order. */
	private Set<String> catalogCodes() {
		return this.languageRepository.findAll()
			.stream()
			.map(LanguageEntity::getCode)
			.collect(Collectors.toCollection(TreeSet::new));
	}

}
