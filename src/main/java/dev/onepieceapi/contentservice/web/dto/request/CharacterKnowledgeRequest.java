package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * What the reader believes of a Character at its first appearance (workflow 3.7.1), for
 * the fields some reveal names; a value left out is not part of it.
 *
 * @param translations the name per language code
 */
public record CharacterKnowledgeRequest(@Size(max = Character.ROMAJI_MAX_LENGTH) String romaji,
		@Size(max = Character.JAPANESE_NAME_MAX_LENGTH) String japaneseName, CharacterStatus status,
		Map<String, @Valid CharacterTranslationRequest> translations) {

}
