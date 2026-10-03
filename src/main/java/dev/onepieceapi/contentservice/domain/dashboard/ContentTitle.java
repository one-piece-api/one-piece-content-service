package dev.onepieceapi.contentservice.domain.dashboard;

import java.util.Map;

/**
 * How a content of any entity type is called where several types are listed together: its
 * name per language, and what to show when the reader's language has none - for a Devil
 * Fruit Type, its romaji. Each entity says how it builds one (implementation plan, D6).
 *
 * @param names the name per language code, for the languages that have one
 */
public record ContentTitle(Map<String, String> names, String fallback) {

}
