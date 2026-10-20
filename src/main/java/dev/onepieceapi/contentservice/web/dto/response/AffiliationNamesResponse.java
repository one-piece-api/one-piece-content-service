package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a list row shows of an Affiliation - the body of its
 * {@link ContentSummaryResponse}: enough to name it, without the texts.
 *
 * @param names the name per language code, for the languages that have one
 */
public record AffiliationNamesResponse(String romaji, Map<String, String> names) {

}
