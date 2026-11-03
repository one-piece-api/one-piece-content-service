package dev.onepieceapi.contentservice.domain.readertime;

import java.util.Map;

/**
 * What the reader calls an item at some point of the story (3.7.1): its romaji and its
 * name per language as known there - e.g. "Gomu Gomu no Mi" before chapter 1044.
 *
 * @param names per language code, for the languages that have one
 */
public record RevealedName(String romaji, Map<String, String> names) {

	public RevealedName {
		names = (names == null) ? Map.of() : Map.copyOf(names);
	}

}
