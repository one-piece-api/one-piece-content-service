package dev.onepieceapi.contentservice.persistence.repository;

import dev.onepieceapi.contentservice.persistence.entity.ImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;

/**
 * The rows of {@code image}; used through {@link JpaImageStore} only.
 */
public interface ImageRepository extends JpaRepository<ImageEntity, String> {

	/**
	 * Deletes the image unless a version still uses it. The foreign key is what
	 * guarantees a used image is never deleted, even when two drafts change at once: this
	 * statement only avoids tripping it in the usual case. Every table that points to
	 * {@code image} is listed here: a new entity with images adds its own. Pending writes
	 * are flushed first, so a version just pointed elsewhere is seen; the persistence
	 * context is not cleared (it holds the version being saved), so a deleted image must
	 * not be read again in the same transaction.
	 * @return 1 if deleted, 0 if still used or already gone
	 */
	@Modifying(flushAutomatically = true)
	@NativeQuery("""
			DELETE FROM image
			WHERE id = :id
			  AND NOT EXISTS (SELECT 1 FROM devil_fruit_version WHERE image_id = :id)""")
	int deleteIfUnreferenced(@Param("id") String id);

}
