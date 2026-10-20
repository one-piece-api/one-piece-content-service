package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import dev.onepieceapi.contentservice.domain.bounty.BountyValidity;

import java.util.List;
import java.util.Map;

/**
 * What a version of a bounty says - the body of its {@link VersionResponse}.
 *
 * @param validity derived, never stored: as the caller sees the other bounties
 * @param previous the bounty it replaces, as it is today; null for none
 * @param next the bounty replacing it, as the caller sees it; null for none
 * @param chain the whole chain through the versions the caller sees, newest first, this
 * bounty included
 * @param translations the description per language code
 * @param image the poster; null for none
 * @param firstAppearance where the poster is revealed; null for none
 */
public record BountyResponse(String posterName, Long amount, BountyCondition condition, boolean inactive,
		BountyValidity validity, BountyReferenceResponse previous, BountyReferenceResponse next,
		List<BountyReferenceResponse> chain, Map<String, BountyTranslationResponse> translations, ImageResponse image,
		FirstAppearanceResponse firstAppearance) {

}
