package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.MarineRank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * A membership of a Character, as a draft is saved (implementation plan of the Character,
 * D10).
 *
 * @param affiliation the content id of the affiliation
 * @param role per language code; a language left out, or blank, is not written yet
 * @param marineRank allowed only in a Navy affiliation; left out for none
 * @param former true once the character has left; left out is false
 */
public record MembershipRequest(@NotNull @Schema(description = "The content id of an Affiliation") UUID affiliation,
		Map<String, @Size(max = Character.ROLE_MAX_LENGTH) String> role, MarineRank marineRank, Boolean former) {

	/** Whether the character has left; not saying so is not. */
	public boolean isFormer() {
		return Boolean.TRUE.equals(this.former);
	}

}
