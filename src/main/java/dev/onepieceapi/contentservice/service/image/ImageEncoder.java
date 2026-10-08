package dev.onepieceapi.contentservice.service.image;

import java.awt.image.BufferedImage;

/**
 * Writes a normalized image in the stored format (implementation plan of the Devil Fruit,
 * D6, refinement 1) - a Strategy, so moving from PNG to WebP later replaces only the
 * implementation: each stored image keeps its own content type.
 */
public interface ImageEncoder {

	String contentType();

	/** The same image always gives the same bytes: the stored id is their hash. */
	byte[] encode(BufferedImage image);

}
