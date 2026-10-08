package dev.onepieceapi.contentservice.domain.image;

/**
 * An image as stored: re-encoded on the profile's canvas, addressed by the SHA-256 of its
 * bytes (implementation plan of the Devil Fruit, D4), so the same upload always gives the
 * same id and is stored once.
 *
 * @param id the SHA-256 of {@code bytes}, lowercase hex
 */
public record NormalizedImage(String id, String contentType, int width, int height, byte[] bytes) {

	public int sizeBytes() {
		return this.bytes.length;
	}

}
