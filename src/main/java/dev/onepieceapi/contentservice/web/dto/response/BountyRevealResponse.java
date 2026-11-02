package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.bounty.BountyField;

import java.util.Map;
import java.util.Set;

/**
 * A reveal of a bounty (workflow 3.7.1): a field named with no value reveals the current
 * one.
 *
 * @param titles the title per language code
 * @param inactive the value learned; null when the current one is revealed
 */
public record BountyRevealResponse(FirstAppearanceResponse revealedAt, Map<String, String> titles,
		Set<BountyField> fields, Boolean inactive) {

}
