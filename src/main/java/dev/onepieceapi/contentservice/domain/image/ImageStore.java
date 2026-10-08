package dev.onepieceapi.contentservice.domain.image;

import java.util.Optional;

/**
 * Where images are kept (implementation plan of the Devil Fruit, D4) - a Repository, so
 * moving them from PostgreSQL to object storage later replaces only the implementation.
 * Called inside the transaction that saves the version, so an image and the version that
 * uses it are written, or not, together.
 */
public interface ImageStore {

	/** Stores the image unless one with its id - the same bytes - is already there. */
	void save(NormalizedImage image);

	Optional<NormalizedImage> find(String id);

	/**
	 * Deletes the image if no version uses it any more: after a draft replaced or removed
	 * it, or was deleted (D5). Does nothing otherwise.
	 */
	void deleteIfUnreferenced(String id);

}
