package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * How a content of any entity type is called where several types are listed together.
 *
 * @param names the name per language code, for the languages that have one
 * @param fallback what to show when the reader's language has no name - for a Devil Fruit
 * Type, its romaji
 */
public record ContentTitleResponse(Map<String, String> names, String fallback) {

}
