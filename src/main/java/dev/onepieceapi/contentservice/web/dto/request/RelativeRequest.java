package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.character.RelativeKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * A relative of a Character, as a draft is saved (implementation plan of the Character,
 * D13).
 *
 * @param relative the content id of the relative
 * @param kind what the relative is to the character: {@code GRANDPARENT} for Garp on
 * Luffy
 */
public record RelativeRequest(@NotNull @Schema(description = "The content id of a Character") UUID relative,
		@NotNull RelativeKind kind) {

}
