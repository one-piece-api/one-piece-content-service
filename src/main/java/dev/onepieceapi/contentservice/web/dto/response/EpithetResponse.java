package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * An epithet of a Character.
 *
 * @param text per language code, for the languages it is written in
 * @param discovered where the reader learns it; null while not chosen
 * @param ended where the reader learns it is over; null for none
 */
public record EpithetResponse(Map<String, String> text, FirstAppearanceResponse discovered,
		FirstAppearanceResponse ended) {

}
