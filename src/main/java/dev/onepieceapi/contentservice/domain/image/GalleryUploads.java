package dev.onepieceapi.contentservice.domain.image;

import java.util.Map;
import java.util.Optional;

/**
 * The files a save uploads for a gallery (workflow 3.5.1; reveals plan R5): each one
 * attached to the gallery entry that shows it, still to be checked and normalized. An
 * entry without an upload keeps an image its draft already has.
 *
 * @param byEntry the upload of each entry, by its index in the gallery
 */
public record GalleryUploads(Map<Integer, byte[]> byEntry) {

	/** Nothing uploaded. */
	public static final GalleryUploads NONE = new GalleryUploads(Map.of());

	public GalleryUploads {
		byEntry = Map.copyOf(byEntry);
	}

	/** The upload of the entry at this index; empty when it keeps a stored image. */
	public Optional<byte[]> of(int index) {
		return Optional.ofNullable(this.byEntry.get(index));
	}

}
