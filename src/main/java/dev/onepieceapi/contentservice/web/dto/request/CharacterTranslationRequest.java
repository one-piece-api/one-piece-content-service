package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.character.Character;
import jakarta.validation.constraints.Size;

/** The localized fields of a Character in one language, as a draft is saved. */
public record CharacterTranslationRequest(@Size(max = Character.NAME_MAX_LENGTH) String name,
		@Size(max = Character.DESCRIPTION_MAX_LENGTH) String description) {

}
