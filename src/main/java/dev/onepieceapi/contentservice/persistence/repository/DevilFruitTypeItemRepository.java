package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeItemEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DevilFruitTypeItemRepository extends JpaRepository<DevilFruitTypeItemEntity, UUID> {

	/** Every currently-published item (Step 5) - the encyclopedia's PUBLISHED rows. */
	List<DevilFruitTypeItemEntity> findByLiveVersionIdIsNotNull();

	/**
	 * Every item with no live pointer - a candidate for the encyclopedia's RETIRED rows
	 * (Step 8) once filtered to those that actually have a published-version history; a
	 * plain never-published draft item also has a null live pointer, so this alone isn't
	 * "retired" yet.
	 */
	List<DevilFruitTypeItemEntity> findByLiveVersionIdIsNull();

}
