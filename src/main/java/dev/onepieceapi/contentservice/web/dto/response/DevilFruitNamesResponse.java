package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a list row shows of a Devil Fruit - the body of its
 * {@link ContentSummaryResponse}: enough to name it and say what it is of, without the
 * texts.
 *
 * @param names the name per language code, for the languages that have one
 * @param type the Devil Fruit Type it belongs to, as it is today; null while not chosen
 */
public record DevilFruitNamesResponse(String romaji, Map<String, String> names, TypeReferenceResponse type) {

}
