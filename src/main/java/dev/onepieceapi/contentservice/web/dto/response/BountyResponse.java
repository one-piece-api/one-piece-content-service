package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import dev.onepieceapi.contentservice.domain.bounty.BountyValidity;

import lombok.Builder;

import java.util.List;

/**
 * What a version of a bounty says - the body of its {@link VersionResponse}.
 *
 * @param validity derived, never stored: as the caller sees the other bounties
 * @param previous the bounty it replaces, as it is today; null for none
 * @param holder the character the poster is about, as it is today; null for none
 * @param next the bounty replacing it, as the caller sees it; null for none
 * @param chain the whole chain through the versions the caller sees, newest first, this
 * bounty included
 * @param image the poster; null for none
 * @param firstAppearance where the poster is revealed; null for none
 * @param description its paragraphs, in order; empty for none
 * @param initialKnowledge what the reader believes at the first appearance (workflow
 * 3.7.1); null for nothing
 * @param reveals what the reader learns later, in order; empty for none
 */
@Builder
public record BountyResponse(String posterName, Long amount, BountyCondition condition, boolean inactive,
		BountyValidity validity, BountyReferenceResponse previous, CharacterReferenceResponse holder,
		BountyReferenceResponse next, List<BountyReferenceResponse> chain, ImageResponse image,
		FirstAppearanceResponse firstAppearance, List<ParagraphResponse> description,
		BountyKnowledgeResponse initialKnowledge, List<BountyRevealResponse> reveals) {

}
