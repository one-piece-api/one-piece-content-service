package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.readertime.ImageKind;

import java.util.Map;

/**
 * An image of a gallery (workflow 3.5.1).
 *
 * @param image the stored image, with where to get it; its id is what a save sends back
 * as {@code imageId} to keep it
 * @param caption per language code, for the languages it is written in
 * @param discovered where the reader learns it; null while not chosen
 * @param ended where the reader learns it is over; null for none
 */
public record GalleryImageResponse(ImageResponse image, ImageKind kind, Map<String, String> caption,
		FirstAppearanceResponse discovered, FirstAppearanceResponse ended) {

}
