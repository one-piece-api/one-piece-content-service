package dev.onepieceapi.contentservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkingRevisionRepository extends JpaRepository<WorkingRevisionEntity, UUID> {

	Optional<WorkingRevisionEntity> findByIdAndAuthorId(UUID id, UUID authorId);

	List<WorkingRevisionEntity> findByAuthorIdAndStatusIn(UUID authorId, Collection<WorkingRevisionStatus> statuses);

	/**
	 * The review-queue-slot check (UF-CNT-03): is a *different* working revision of this
	 * item already occupying it?
	 */
	boolean existsByItemIdAndStatusAndIdNot(UUID itemId, WorkingRevisionStatus status, UUID excludedId);

	/** The shared review queue (UF-CNT-13+), oldest-submitted first. */
	List<WorkingRevisionEntity> findByStatusOrderByUpdatedAtAsc(WorkingRevisionStatus status);

	/**
	 * The supersession check at approve time (4.1): any *other* active candidate of this
	 * item?
	 */
	List<WorkingRevisionEntity> findByItemIdAndStatusAndIdNot(UUID itemId, WorkingRevisionStatus status,
			UUID excludedId);

}
