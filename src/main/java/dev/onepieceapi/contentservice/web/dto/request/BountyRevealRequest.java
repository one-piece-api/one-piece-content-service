package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.bounty.BountyField;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;

/**
 * A reveal of a bounty (workflow 3.7.1), see {@link RevealRequest}: {@code INACTIVE} is
 * learned with {@code inactive}.
 */
public record BountyRevealRequest(FirstAppearanceRequest revealedAt,
		Map<String, @Size(max = Reveal.TITLE_MAX_LENGTH) String> titles, Set<@NotNull BountyField> fields,
		Boolean inactive) implements RevealRequest<BountyField> {

}
