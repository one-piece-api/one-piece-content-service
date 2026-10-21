package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * What a list row shows of a Character - the body of its {@link ContentSummaryResponse}:
 * enough to name it, without the texts.
 *
 * @param names the name per language code, for the languages that have one
 * @param bountyCount how many bounties name it as holder for the caller: the total of the
 * list of bounties narrowed by it
 */
public record CharacterNamesResponse(String romaji, Map<String, String> names, long bountyCount) {

}
