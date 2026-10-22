package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;
import java.util.UUID;

/**
 * A Devil Fruit as a character may point to it: the fruit as of today, never what it said
 * when it was written.
 *
 * @param id the content id of the fruit
 * @param romaji null when the fruit has nothing approved to call it by
 * @param names the name per language code, for the languages that have one
 * @param multipleUsers whether several characters may use it at the same time
 */
public record DevilFruitReferenceResponse(UUID id, String romaji, Map<String, String> names, boolean multipleUsers) {

}
