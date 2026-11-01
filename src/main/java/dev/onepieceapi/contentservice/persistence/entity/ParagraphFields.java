package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;

import java.util.Map;

/**
 * What a paragraph row says, as {@link ParagraphEntity#rewrite} writes it.
 *
 * @param text per language code
 * @param window null for none chosen
 */
public record ParagraphFields(ParagraphSection section, Map<String, String> text, StoryWindowEmbeddable window) {
}
