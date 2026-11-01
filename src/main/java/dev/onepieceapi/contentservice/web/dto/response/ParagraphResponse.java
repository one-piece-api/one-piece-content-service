package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * A paragraph of a long text (workflow 3.7.3).
 *
 * @param text per language code, for the languages it is written in
 * @param discovered where the reader learns it; null while not chosen
 * @param ended where the reader learns it is over; null for none
 */
public record ParagraphResponse(Map<String, String> text, FirstAppearanceResponse discovered,
		FirstAppearanceResponse ended) {

}
