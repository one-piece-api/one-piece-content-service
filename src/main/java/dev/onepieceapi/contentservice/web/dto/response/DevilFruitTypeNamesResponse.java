package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a list row shows of a Devil Fruit Type - the body of its
 * {@link ContentSummaryResponse}: enough to name it, without the descriptions.
 *
 * @param names the name per language code, for the languages that have one
 */
public record DevilFruitTypeNamesResponse(String romaji, Map<String, String> names) {

}
