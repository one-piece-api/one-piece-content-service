package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a list row shows of a Devil Fruit Type - the body of its
 * {@link ContentSummaryResponse}: enough to name it, without the descriptions.
 *
 * @param names the name per language code, for the languages that have one
 * @param devilFruitCount how many fruits point to it for the caller: the total of the
 * list of Devil Fruits narrowed to this type
 */
public record DevilFruitTypeNamesResponse(String romaji, Map<String, String> names, long devilFruitCount) {

}
