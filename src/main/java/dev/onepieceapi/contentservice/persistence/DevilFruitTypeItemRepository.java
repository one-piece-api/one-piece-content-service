package dev.onepieceapi.contentservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DevilFruitTypeItemRepository extends JpaRepository<DevilFruitTypeItemEntity, UUID> {

	/** Every currently-published item (Step 5) - the encyclopedia's PUBLISHED rows. */
	List<DevilFruitTypeItemEntity> findByLiveVersionIdIsNotNull();

}
