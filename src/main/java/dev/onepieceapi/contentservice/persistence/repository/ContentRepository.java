package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;

import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
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

}
