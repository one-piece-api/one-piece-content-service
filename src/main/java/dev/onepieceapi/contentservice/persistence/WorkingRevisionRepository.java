package dev.onepieceapi.contentservice.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkingRevisionRepository extends JpaRepository<WorkingRevisionEntity, UUID> {

	Optional<WorkingRevisionEntity> findByIdAndAuthorId(UUID id, UUID authorId);

	List<WorkingRevisionEntity> findByAuthorIdAndStatusIn(UUID authorId, Collection<WorkingRevisionStatus> statuses);

}
