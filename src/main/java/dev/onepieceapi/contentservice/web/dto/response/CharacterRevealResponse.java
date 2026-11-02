package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.character.CharacterField;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;

import java.util.Map;
import java.util.Set;

/**
 * A reveal of a Character (workflow 3.7.1): a field named with no value reveals the
 * current one.
 *
 * @param titles the title per language code
 * @param translations the name learned per language code
 */
public record CharacterRevealResponse(FirstAppearanceResponse revealedAt, Map<String, String> titles,
		Set<CharacterField> fields, String romaji, String japaneseName, CharacterStatus status,
		Map<String, CharacterTranslationResponse> translations) {

}
