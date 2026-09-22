package dev.onepieceapi.contentservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContentVersionRepository extends JpaRepository<ContentVersionEntity, UUID> {

	/** Drives the next {@code sequenceNumber} at publish time. */
	long countByItemId(UUID itemId);

}
