package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.readertime.ImageKind;

import java.util.Map;

/**
 * What a gallery image row says, as {@link CharacterImageEntity#rewrite} writes it.
 *
 * @param caption per language code
 * @param window null for none chosen
 */
public record GalleryImageFields(String imageId, ImageKind kind, Map<String, String> caption,
		StoryWindowEmbeddable window) {

}
