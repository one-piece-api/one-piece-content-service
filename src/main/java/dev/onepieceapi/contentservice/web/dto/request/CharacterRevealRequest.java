package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterField;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;

/**
 * A reveal of a Character (workflow 3.7.1), see {@link RevealRequest}: {@code IDENTITY}
 * is learned with {@code romaji}, {@code japaneseName} and {@code translations} together,
 * {@code STATUS} with {@code status}.
 */
public record CharacterRevealRequest(FirstAppearanceRequest revealedAt,
		Map<String, @Size(max = Reveal.TITLE_MAX_LENGTH) String> titles, Set<@NotNull CharacterField> fields,
		@Size(max = Character.ROMAJI_MAX_LENGTH) String romaji,
		@Size(max = Character.JAPANESE_NAME_MAX_LENGTH) String japaneseName, CharacterStatus status,
		Map<String, @Valid CharacterTranslationRequest> translations) implements RevealRequest<CharacterField> {

}
