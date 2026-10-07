package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The contents themselves, of whatever entity type. A content says nothing by itself, so
 * all there is to do with one is to create it, and to remove it with its first draft:
 * everything else goes through its versions.
 */
public interface ContentRepository extends Repository<ContentEntity, UUID> {

	ContentEntity save(ContentEntity content);

	void deleteById(UUID id);

	List<ContentEntity> findByIdIn(Collection<UUID> ids);

	/**
	 * Holds the content shared until the transaction ends: others holding it shared go
	 * on, whoever wants it exclusively waits. For whoever relies on the content staying
	 * as it is - e.g. a fruit going online while its type is.
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select c from ContentEntity c where c.id = :id")
	Optional<ContentEntity> lockShared(UUID id);

	/**
	 * Holds the content exclusively until the transaction ends: waits for every shared
	 * holder, and makes the others wait. For whoever is about to change what they rely on
	 * - e.g. taking a type offline.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from ContentEntity c where c.id = :id")
	Optional<ContentEntity> lockExclusive(UUID id);

}
