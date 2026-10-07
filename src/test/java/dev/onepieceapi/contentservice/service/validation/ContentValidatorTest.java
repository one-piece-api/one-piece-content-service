package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.persistence.entity.LanguageEntity;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.VersionBodyRepository;
import dev.onepieceapi.contentservice.service.exception.TranslationLanguageUnknownException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import dev.onepieceapi.exception.ApplicationException;
import dev.onepieceapi.exception.web.FieldViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The rules every content meets, checked on a body that exists only here: what is
 * required comes from the body itself, which languages and names it has from what every
 * body tells - nothing of the Devil Fruit Type.
 */
class ContentValidatorTest {

	private static final UUID CONTENT_ID = UUID.randomUUID();

	@SuppressWarnings("unchecked")
	private final VersionBodyRepository<VersionBodyEntity> repository = mock(VersionBodyRepository.class);

	private final LanguageRepository languageRepository = mock(LanguageRepository.class);

	private final ContentValidator<Note, VersionBodyEntity> validator = new ContentValidator<>(this.repository,
			this.languageRepository, entity -> null);

	@BeforeEach
	void setUp() {
		when(this.languageRepository.findAll())
			.thenReturn(List.of(new LanguageEntity("it", "Italiano"), new LanguageEntity("en", "English")));
		when(this.repository.slugOf(anyString())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
	}

	@Test
	void aSubmissionMissesWhatTheBodySaysIsMissingInTheLanguagesOfTheCatalogInOrder() {
		var note = new Note("memo", Set.of(), Map.of(), List.of("translations[en].name", "translations[it].name"));

		var refused = catchThrowableOfType(VersionIncompleteException.class,
				() -> this.validator.validateSubmission(CONTENT_ID, 1, note));

		assertThat(violationsOf(refused)).extracting(FieldViolation::field)
			.containsExactly("translations[en].name", "translations[it].name");
		assertThat(violationsOf(refused)).extracting(FieldViolation::message).containsOnly("is required for review");
		assertThat(note.askedFor).containsExactly("en", "it");
	}

	@Test
	void aLanguageOutsideTheCatalogIsRefusedEvenWithoutAName() {
		var note = new Note("memo", Set.of("fr"), Map.of(), List.of());

		var refused = catchThrowableOfType(TranslationLanguageUnknownException.class,
				() -> this.validator.validateDraft(CONTENT_ID, note));

		assertThat(refused.getDetails()).containsEntry("languages", List.of("fr"));
	}

	@Test
	void aNameAnotherContentHasIsRefusedInItsLanguage() {
		var note = new Note("memo", Set.of("it"), Map.of("it", "Nota"), List.of());
		when(this.repository.nameIsTakenByAnother("it", "Nota", CONTENT_ID)).thenReturn(true);

		var refused = catchThrowableOfType(ValueAlreadyUsedException.class,
				() -> this.validator.validateDraft(CONTENT_ID, note));

		assertThat(violationsOf(refused)).extracting(FieldViolation::field).containsExactly("translations[it].name");
	}

	@SuppressWarnings("unchecked")
	private static List<FieldViolation> violationsOf(ApplicationException refused) {
		return (List<FieldViolation>) refused.getDetails().get("errors");
	}

	/** A body telling exactly what the test wants, and remembering what it was asked. */
	static final class Note implements ContentBody<Note> {

		private final String romaji;

		private final Set<String> languages;

		private final Map<String, String> names;

		private final List<String> missing;

		private Collection<String> askedFor = List.of();

		Note(String romaji, Set<String> languages, Map<String, String> names, List<String> missing) {
			this.romaji = romaji;
			this.languages = languages;
			this.names = names;
			this.missing = missing;
		}

		@Override
		public String romaji() {
			return this.romaji;
		}

		@Override
		public Set<String> languages() {
			return this.languages;
		}

		@Override
		public Map<String, String> names() {
			return this.names;
		}

		@Override
		public Note normalized() {
			return this;
		}

		@Override
		public List<String> missingFields(Collection<String> catalog) {
			this.askedFor = List.copyOf(catalog);
			return this.missing;
		}

	}

}
