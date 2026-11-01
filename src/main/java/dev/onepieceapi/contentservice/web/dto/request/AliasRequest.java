package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.character.Character;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An alias of a Character, as a draft is saved: {@code Sogeking} on Usopp.
 *
 * @param alias the name; whether it is blank or repeated is for the service to say
 * @param discovered as {@link WindowedRequest#discovered()}
 * @param ended as {@link WindowedRequest#ended()}
 */
public record AliasRequest(@NotNull @Size(max = Character.ALIAS_MAX_LENGTH) String alias,
		FirstAppearanceRequest discovered, FirstAppearanceRequest ended) implements WindowedRequest {

}
