package dev.onepieceapi.contentservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TranslationRepository extends JpaRepository<TranslationEntity, TranslationId> {

	List<TranslationEntity> findByIdWorkingRevisionId(UUID workingRevisionId);

	/** Clears a working revision's translations ahead of deleting the revision itself. */
	void deleteByIdWorkingRevisionId(UUID workingRevisionId);

}
