package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.persistence.entity.ContentVersionTranslationEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionTranslationId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContentVersionTranslationRepository
		extends JpaRepository<ContentVersionTranslationEntity, ContentVersionTranslationId> {

	List<ContentVersionTranslationEntity> findByIdContentVersionId(UUID contentVersionId);

	/** Step 10: whether any published version snapshot still references this language. */
	boolean existsByIdLanguageCode(String languageCode);

}
