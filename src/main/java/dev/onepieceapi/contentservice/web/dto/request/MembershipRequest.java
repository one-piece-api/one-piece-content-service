package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.character.Character;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * A membership of a Character, as a draft is saved (implementation plan of the Character,
 * D10; workflow 3.4.7): the affiliation and every stay in it.
 *
 * @param affiliation the content id of the affiliation
 * @param periods in order; left out for none, which a draft may be - one is required to
 * submit
 */
public record MembershipRequest(@NotNull @Schema(description = "The content id of an Affiliation") UUID affiliation,
		@Size(max = Character.MAX_PERIODS) List<@NotNull @Valid MembershipPeriodRequest> periods) {

}
