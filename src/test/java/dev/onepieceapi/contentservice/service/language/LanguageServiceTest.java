package dev.onepieceapi.contentservice.service.language;

import dev.onepieceapi.contentservice.service.audit.AuditLogService;

import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.persistence.entity.LanguageEntity;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.persistence.repository.VersionBodyRepository;
import dev.onepieceapi.contentservice.service.exception.LanguageInUseException;
import dev.onepieceapi.contentservice.service.validation.LanguageValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A language is in use if the versions of any entity say something in it, not only those
 * of the first one (implementation plan of the Devil Fruit, D8): one repository per
 * entity, asked all together.
 */
class LanguageServiceTest {

	private final User luffy = new User(UUID.randomUUID(), "luffy", "luffy@onepiece.local");

	private final LanguageRepository languageRepository = mock(LanguageRepository.class);

	@SuppressWarnings("unchecked")
	private final VersionBodyRepository<?> firstEntity = mock(VersionBodyRepository.class);

	@SuppressWarnings("unchecked")
	private final VersionBodyRepository<?> secondEntity = mock(VersionBodyRepository.class);

	private final LanguageService service = new LanguageService(this.languageRepository,
			List.of(this.firstEntity, this.secondEntity), mock(AuditLogService.class), new LanguageValidator());

	@Test
	void aLanguageUsedOnlyByTheSecondEntityCannotBeDeleted() {
		var language = new LanguageEntity("fr", "Francais");
		when(this.languageRepository.findById("fr")).thenReturn(Optional.of(language));
		when(this.firstEntity.existsByLanguage("fr")).thenReturn(false);
		when(this.secondEntity.existsByLanguage("fr")).thenReturn(true);

		assertThatThrownBy(() -> this.service.delete("fr", this.luffy)).isInstanceOf(LanguageInUseException.class);

		verify(this.languageRepository, never()).delete(language);
	}

	@Test
	void aLanguageNoEntityUsesIsDeleted() {
		var language = new LanguageEntity("fr", "Francais");
		when(this.languageRepository.findById("fr")).thenReturn(Optional.of(language));

		this.service.delete("fr", this.luffy);

		verify(this.languageRepository).delete(language);
	}

}
