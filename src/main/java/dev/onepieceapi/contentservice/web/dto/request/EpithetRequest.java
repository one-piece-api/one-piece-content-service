package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.character.Character;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * An epithet of a Character, as a draft is saved.
 *
 * @param text per language code; a language left out, or blank, is not written yet
 */
public record EpithetRequest(Map<String, @Size(max = Character.EPITHET_MAX_LENGTH) String> text) {

}
