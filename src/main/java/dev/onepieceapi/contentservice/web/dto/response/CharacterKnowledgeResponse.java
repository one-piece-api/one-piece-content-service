package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.character.CharacterStatus;

import java.util.Map;

/**
 * What the reader believes of a Character at its first appearance (workflow 3.7.1); a
 * value not part of it is null.
 *
 * @param translations the name per language code
 */
public record CharacterKnowledgeResponse(String romaji, String japaneseName, CharacterStatus status,
		Map<String, CharacterTranslationResponse> translations) {

}
