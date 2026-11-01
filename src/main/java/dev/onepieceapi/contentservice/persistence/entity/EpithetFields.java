package dev.onepieceapi.contentservice.persistence.entity;

import java.util.Map;

/**
 * What an epithet row says, as {@link CharacterEpithetEntity#rewrite} writes it.
 *
 * @param text per language code
 * @param window null for none chosen
 */
public record EpithetFields(Map<String, String> text, StoryWindowEmbeddable window) {

}
