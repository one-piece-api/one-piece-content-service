package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.persistence.entity.TranslationEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TranslationRepository extends JpaRepository<TranslationEntity, TranslationId> {

	List<TranslationEntity> findByIdWorkingRevisionId(UUID workingRevisionId);

	/** Clears a working revision's translations ahead of deleting the revision itself. */
	void deleteByIdWorkingRevisionId(UUID workingRevisionId);

	/** Step 10: whether any in-progress content still references this language. */
	boolean existsByIdLanguageCode(String languageCode);

	/**
	 * The name-uniqueness check (3.3): does this language+name collide with a "reserving"
	 * working revision belonging to another item?
	 */
	boolean existsByIdLanguageCodeAndNameIgnoreCaseAndIdWorkingRevisionIdIn(String languageCode, String name,
			Collection<UUID> workingRevisionIds);

}
