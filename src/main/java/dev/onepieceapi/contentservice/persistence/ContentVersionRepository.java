package dev.onepieceapi.contentservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentVersionRepository extends JpaRepository<ContentVersionEntity, UUID> {

	/** Drives the next {@code sequenceNumber} at publish time. */
	long countByItemId(UUID itemId);

	/** Step 7's "Storico versioni": every snapshot of one item, most recent first. */
	List<ContentVersionEntity> findByItemIdOrderBySequenceNumberDesc(UUID itemId);

	/** Scopes Rollback's target to the item it claims to belong to. */
	Optional<ContentVersionEntity> findByIdAndItemId(UUID id, UUID itemId);

}
